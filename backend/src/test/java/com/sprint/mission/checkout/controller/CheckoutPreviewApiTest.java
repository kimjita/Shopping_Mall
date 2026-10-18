package com.sprint.mission.checkout.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.service.AdminCouponService;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.AdminSkuService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class CheckoutPreviewApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    CartService cartService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminSkuService adminSkuService;

    @Autowired
    AdminCouponService adminCouponService;

    @Autowired
    CouponService couponService;

    @Autowired
    PointService pointService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;
    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private String token() {
        return bearer(memberId, Role.USER);
    }

    /** 옵션 하나짜리 상품을 만들어 장바구니에 담고 cart_item id를 돌려준다. */
    private Long cartLine(String name, long basePrice, int stock, int quantity, Integer salePercent) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, basePrice, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, stock))));
        if (salePercent != null) {
            Instant now = Instant.now();
            adminProductService.startSale(productId,
                    new SaleRequest(salePercent, now.minusSeconds(3_600), now.plusSeconds(3_600)));
        }
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(memberId, new CartAddRequest(skuId, quantity));
        return jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, skuId);
    }

    /** 15% 세일 중인 14,900원 상품 2개 줄이다. 정가 29,800원, 세일가 25,320원이다. */
    private Long saleLine() {
        return cartLine("1025 독도 토너", 14_900, 10, 2, 15);
    }

    private Long memberCouponId(Long ownerId, Long couponId) {
        couponService.download(ownerId, couponId);
        return jdbcTemplate.queryForObject("select id from member_coupon where member_id = ? and coupon_id = ?",
                Long.class, ownerId, couponId);
    }

    private ResultActions preview(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/members/me/checkout/preview")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions preview(String body) throws Exception {
        return preview(token(), body);
    }

    private static String body(String cartItemIds, Object memberCouponId, long points) {
        return "{\"cartItemIds\":" + cartItemIds + ",\"memberCouponId\":" + memberCouponId + ",\"points\":" + points + "}";
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void 선택한_장바구니_줄로_금액을_미리_본다() throws Exception {
        Long lineId = saleLine();
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 10, null)).id();
        Long memberCouponId = memberCouponId(memberId, couponId);
        pointService.grant(memberId, 5_000, "이벤트");

        preview(body("[" + lineId + "]", memberCouponId, 1_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].cartItemId").value(lineId))
                .andExpect(jsonPath("$.lines[0].productName").value("1025 독도 토너"))
                .andExpect(jsonPath("$.lines[0].skuName").value("기본"))
                .andExpect(jsonPath("$.lines[0].thumbnailUrl").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(14_900))
                .andExpect(jsonPath("$.lines[0].unitSalePrice").value(12_660))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(25_320))
                .andExpect(jsonPath("$.price.itemsPrice").value(29_800))
                .andExpect(jsonPath("$.price.saleDiscount").value(4_480))
                .andExpect(jsonPath("$.price.itemsSalePrice").value(25_320))
                .andExpect(jsonPath("$.price.couponDiscount").value(3_000))
                .andExpect(jsonPath("$.price.shippingFee").value(0))
                .andExpect(jsonPath("$.price.pointsUsed").value(1_000))
                .andExpect(jsonPath("$.price.paymentAmount").value(21_320))
                .andExpect(jsonPath("$.pointBalance").value(5_000))
                .andDo(document("checkout/preview",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("cartItemIds").type(JsonFieldType.ARRAY)
                                        .description("주문할 장바구니 항목 ID (1~50개, 서로 달라야 한다)"),
                                fieldWithPath("memberCouponId").type(JsonFieldType.NUMBER).optional()
                                        .description("사용할 내 쿠폰 ID. 쓰지 않으면 `null`이거나 생략한다"),
                                fieldWithPath("points").type(JsonFieldType.NUMBER)
                                        .description("사용할 적립금 (원, 0 이상)")),
                        responseFields(
                                fieldWithPath("lines").type(JsonFieldType.ARRAY)
                                        .description("주문할 줄. 요청한 `cartItemIds` 순서 그대로다"),
                                fieldWithPath("lines[].cartItemId").type(JsonFieldType.NUMBER)
                                        .description("장바구니 항목 ID"),
                                fieldWithPath("lines[].skuId").type(JsonFieldType.NUMBER).description("옵션(SKU) ID"),
                                fieldWithPath("lines[].productName").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("lines[].skuName").type(JsonFieldType.STRING).description("옵션 이름"),
                                fieldWithPath("lines[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("대표 이미지 URL. 상품에 이미지가 없으면 null이다"),
                                fieldWithPath("lines[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                                fieldWithPath("lines[].unitPrice").type(JsonFieldType.NUMBER)
                                        .description("옵션 정가 (원)"),
                                fieldWithPath("lines[].unitSalePrice").type(JsonFieldType.NUMBER)
                                        .description("옵션 판매가 (원). 진행 중인 세일을 적용한 값이다"),
                                fieldWithPath("lines[].lineTotal").type(JsonFieldType.NUMBER)
                                        .description("줄 합계 (원). `unitSalePrice` × `quantity`다"),
                                fieldWithPath("price").type(JsonFieldType.OBJECT).description("금액 계산 결과"),
                                fieldWithPath("price.itemsPrice").type(JsonFieldType.NUMBER)
                                        .description("정가 합계 (원)"),
                                fieldWithPath("price.saleDiscount").type(JsonFieldType.NUMBER)
                                        .description("세일 할인 금액 (원). 정가 합계에서 세일가 합계를 뺀 값이다"),
                                fieldWithPath("price.itemsSalePrice").type(JsonFieldType.NUMBER)
                                        .description("세일가 합계 (원)"),
                                fieldWithPath("price.couponDiscount").type(JsonFieldType.NUMBER)
                                        .description("쿠폰 할인 금액 (원). 쿠폰을 쓰지 않으면 0이다"),
                                fieldWithPath("price.shippingFee").type(JsonFieldType.NUMBER)
                                        .description("배송비 (원). 쿠폰 적용 후 금액이 20,000원 이상이면 0, 아니면 2,500이다"),
                                fieldWithPath("price.pointsUsed").type(JsonFieldType.NUMBER)
                                        .description("사용할 적립금 (원)"),
                                fieldWithPath("price.paymentAmount").type(JsonFieldType.NUMBER)
                                        .description("결제 금액 (원)"),
                                fieldWithPath("pointBalance").type(JsonFieldType.NUMBER)
                                        .description("미리보기 시점의 적립금 잔액 (원)"))));
    }

    @Test
    void 쿠폰과_적립금_없이도_미리_본다() throws Exception {
        Long lineId = cartLine("선크림", 10_000, 10, 1, null);

        preview("{\"cartItemIds\":[" + lineId + "],\"points\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.itemsPrice").value(10_000))
                .andExpect(jsonPath("$.price.saleDiscount").value(0))
                .andExpect(jsonPath("$.price.couponDiscount").value(0))
                .andExpect(jsonPath("$.price.shippingFee").value(2_500))
                .andExpect(jsonPath("$.price.paymentAmount").value(12_500))
                .andExpect(jsonPath("$.pointBalance").value(0));
    }

    @Test
    void 장바구니_가격과_미리보기_가격이_같다() throws Exception {
        Long first = saleLine();
        Long second = cartLine("마스크팩", 3_000, 10, 3, null);

        JsonNode cartItems = json(mockMvc.perform(get("/api/members/me/cart")
                .header(HttpHeaders.AUTHORIZATION, token()))).get("items");
        JsonNode lines = json(preview(body("[" + second + "," + first + "]", null, 0))
                .andExpect(status().isOk())).get("lines");

        // 미리보기는 요청한 순서(second, first)로 내려가고, 장바구니는 담은 순서(first, second)다.
        assertThat(lines.get(0).get("cartItemId").asLong()).isEqualTo(second);
        assertThat(lines.get(1).get("cartItemId").asLong()).isEqualTo(first);
        for (JsonNode cartItem : cartItems) {
            JsonNode line = lines.get(cartItem.get("id").asLong() == first ? 1 : 0);
            assertThat(line.get("unitPrice")).isEqualTo(cartItem.get("unitPrice"));
            assertThat(line.get("unitSalePrice")).isEqualTo(cartItem.get("unitSalePrice"));
            assertThat(line.get("lineTotal")).isEqualTo(cartItem.get("lineTotal"));
            assertThat(line.get("productName")).isEqualTo(cartItem.get("productName"));
            assertThat(line.get("skuName")).isEqualTo(cartItem.get("skuName"));
            assertThat(line.get("thumbnailUrl")).isEqualTo(cartItem.get("thumbnailUrl"));
        }
    }

    @Test
    void 다른_회원의_장바구니_줄이면_404() throws Exception {
        Long mine = saleLine();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        Long skuId = jdbcTemplate.queryForObject("select sku_id from cart_item where id = ?", Long.class, mine);
        cartService.add(other, new CartAddRequest(skuId, 1));
        Long othersLine = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class, other);

        preview(body("[" + mine + "," + othersLine + "]", null, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        preview(body("[999999]", null, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void 다른_회원의_쿠폰이면_404_COUPON_NOT_FOUND() throws Exception {
        Long lineId = saleLine();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 10, null)).id();
        Long othersCoupon = memberCouponId(other, couponId);

        preview(body("[" + lineId + "]", othersCoupon, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
        preview(body("[" + lineId + "]", 999_999, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    void 품절_줄이_있으면_409_CART_ITEM_UNAVAILABLE() throws Exception {
        Long ok = saleLine();
        Long soldOut = cartLine("품절될 토너", 10_000, 5, 2, null);
        Long productId = jdbcTemplate.queryForObject(
                "select s.product_id from cart_item c join sku s on s.id = c.sku_id where c.id = ?", Long.class, soldOut);
        Long skuId = jdbcTemplate.queryForObject("select sku_id from cart_item where id = ?", Long.class, soldOut);
        adminSkuService.adjustStock(productId, skuId, new StockAdjustRequest(-5));

        preview(body("[" + ok + "," + soldOut + "]", null, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_ITEM_UNAVAILABLE"));
        // 품절 줄을 빼면 미리볼 수 있다.
        preview(body("[" + ok + "]", null, 0)).andExpect(status().isOk());
    }

    @Test
    void 사용한_쿠폰이나_기간이_지난_쿠폰은_400_COUPON_NOT_USABLE() throws Exception {
        Long lineId = saleLine();
        Long usedCouponId = adminCouponService.create(CouponFixtures.fixed("사용한 쿠폰", 10, null)).id();
        Long usedMemberCoupon = memberCouponId(memberId, usedCouponId);
        jdbcTemplate.update("update member_coupon set status = 'USED' where id = ?", usedMemberCoupon);
        Long expiredCouponId = adminCouponService.create(CouponFixtures.fixed("지난 쿠폰", 10, null)).id();
        Long expiredMemberCoupon = memberCouponId(memberId, expiredCouponId);
        jdbcTemplate.update("update coupon set use_ends_at = now() - interval '1 hour' where id = ?", expiredCouponId);

        preview(body("[" + lineId + "]", usedMemberCoupon, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_USABLE"));
        preview(body("[" + lineId + "]", expiredMemberCoupon, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_USABLE"));
    }

    @Test
    void 최소_주문금액_미달_쿠폰은_400_COUPON_NOT_APPLICABLE() throws Exception {
        Long lineId = saleLine();
        Long couponId = adminCouponService.create(CouponFixtures.fixed("10만원 이상", 10, null)).id();
        Long memberCouponId = memberCouponId(memberId, couponId);
        jdbcTemplate.update("update coupon set min_order_amount = 100000 where id = ?", couponId);

        preview(body("[" + lineId + "]", memberCouponId, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_APPLICABLE"));
    }

    @Test
    void 최소_주문금액_미달_쿠폰과_잔액_초과_적립금이_함께면_POINTS_NOT_ENOUGH가_먼저다() throws Exception {
        Long lineId = saleLine();
        Long couponId = adminCouponService.create(CouponFixtures.fixed("10만원 이상", 10, null)).id();
        Long memberCouponId = memberCouponId(memberId, couponId);
        jdbcTemplate.update("update coupon set min_order_amount = 100000 where id = ?", couponId);

        // 잔액은 0원이다. 잔액 확인(5단계)이 계산기의 쿠폰 최소 주문금액 확인(6단계)보다 앞선다.
        preview(body("[" + lineId + "]", memberCouponId, 1_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINTS_NOT_ENOUGH"));
    }

    @Test
    void 잔액보다_많은_적립금은_409_POINTS_NOT_ENOUGH() throws Exception {
        Long lineId = saleLine();
        pointService.grant(memberId, 1_000, "이벤트");

        preview(body("[" + lineId + "]", null, 1_001))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINTS_NOT_ENOUGH"));
        // 잔액과 같으면 쓸 수 있다.
        preview(body("[" + lineId + "]", null, 1_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.paymentAmount").value(24_320));
    }

    @Test
    void 결제금액이_100원_미만이_되는_적립금은_400() throws Exception {
        Long lineId = saleLine();
        pointService.grant(memberId, 30_000, "이벤트");

        // 세일가 합계 25,320원, 배송비 0원이므로 25,221원부터는 결제금액이 100원 미만이다.
        preview(body("[" + lineId + "]", null, 25_221))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINTS_EXCEED_LIMIT"));
        preview(body("[" + lineId + "]", null, 25_220))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.paymentAmount").value(100));
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        Long lineId = saleLine();
        String fiftyOne = "[" + String.join(",", Collections.nCopies(51, lineId.toString())) + "]";
        String fiftyOneDistinct = "[" + LongStream.rangeClosed(1, 51)
                .mapToObj(Long::toString).collect(Collectors.joining(",")) + "]";

        preview("{\"cartItemIds\":[],\"points\":0}").andExpect(status().isBadRequest());
        preview("{\"points\":0}").andExpect(status().isBadRequest());
        preview(body(fiftyOneDistinct, null, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        preview(body(fiftyOne, null, 0)).andExpect(status().isBadRequest());
        preview(body("[" + lineId + "," + lineId + "]", null, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        preview(body("[" + lineId + "]", null, -1)).andExpect(status().isBadRequest());
        preview(body("[" + lineId + ",null]", null, 0)).andExpect(status().isBadRequest());
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(post("/api/members/me/checkout/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1]", null, 0)))
                .andExpect(status().isUnauthorized());
    }
}
