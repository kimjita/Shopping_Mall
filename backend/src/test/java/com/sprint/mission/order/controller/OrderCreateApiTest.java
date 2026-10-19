package com.sprint.mission.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.service.AdminCouponService;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.AdminSkuService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

class OrderCreateApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    AddressService addressService;

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
    Long addressId;
    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        addressId = address(memberId);
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private String token() {
        return bearer(memberId, Role.USER);
    }

    private Long address(Long ownerId) {
        return addressService.create(ownerId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
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

    /** 15% 세일 중인 14,900원 상품 2개 줄이다(재고 10). 정가 29,800원, 세일가 25,320원이다. */
    private Long saleLine() {
        return cartLine("1025 독도 토너", 14_900, 10, 2, 15);
    }

    private Long memberCouponId(Long ownerId, Long couponId) {
        couponService.download(ownerId, couponId);
        return jdbcTemplate.queryForObject("select id from member_coupon where member_id = ? and coupon_id = ?",
                Long.class, ownerId, couponId);
    }

    private Long newMemberCoupon() {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 10, null)).id();
        return memberCouponId(memberId, couponId);
    }

    private ResultActions order(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/members/me/orders")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions order(String body) throws Exception {
        return order(token(), body);
    }

    private static String body(String cartItemIds, Object memberCouponId, long points, Object addressId) {
        return "{\"cartItemIds\":" + cartItemIds + ",\"memberCouponId\":" + memberCouponId + ",\"points\":" + points
                + ",\"addressId\":" + addressId + "}";
    }

    private String body(Long lineId, Object memberCouponId, long points) {
        return body("[" + lineId + "]", memberCouponId, points, addressId);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private int stockOf(Long cartItemId) {
        return jdbcTemplate.queryForObject(
                "select s.stock from cart_item c join sku s on s.id = c.sku_id where c.id = ?", Integer.class, cartItemId);
    }

    private long balance() {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    private Map<String, Object> orderRow(String orderNumber) {
        return jdbcTemplate.queryForMap("select * from orders where order_number = ?", orderNumber);
    }

    private Map<String, Object> memberCoupon(Long memberCouponId) {
        return jdbcTemplate.queryForMap("select status, used_order_id from member_coupon where id = ?", memberCouponId);
    }

    @Test
    void 장바구니_줄로_결제대기_주문을_만든다() throws Exception {
        Long lineId = saleLine();
        Long memberCouponId = newMemberCoupon();
        pointService.grant(memberId, 5_000, "이벤트");
        JsonNode previewPrice = json(mockMvc.perform(post("/api/members/me/checkout/preview")
                .header(HttpHeaders.AUTHORIZATION, token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartItemIds\":[" + lineId + "],\"memberCouponId\":" + memberCouponId
                        + ",\"points\":1000}"))
                .andExpect(status().isOk())).get("price");

        ResultActions result = order(body(lineId, memberCouponId, 1_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("1025 독도 토너"))
                .andExpect(jsonPath("$.items[0].skuName").value("기본"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://img.example.com/main.jpg"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(14_900))
                .andExpect(jsonPath("$.items[0].unitSalePrice").value(12_660))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25_320))
                .andExpect(jsonPath("$.price.paymentAmount").value(21_320))
                .andExpect(jsonPath("$.couponName").value("3천원 쿠폰"))
                .andExpect(jsonPath("$.shipping.recipient").value("홍길동"))
                .andExpect(jsonPath("$.shipping.zipCode").value("06236"))
                .andExpect(jsonPath("$.shipping.address2").value("101호"))
                .andExpect(jsonPath("$.canceledAt").doesNotExist())
                .andDo(document("order/create",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("cartItemIds").type(JsonFieldType.ARRAY)
                                        .description("주문할 장바구니 항목 ID (1~50개, 서로 달라야 한다)"),
                                fieldWithPath("memberCouponId").type(JsonFieldType.NUMBER).optional()
                                        .description("사용할 내 쿠폰 ID. 쓰지 않으면 `null`이거나 생략한다"),
                                fieldWithPath("points").type(JsonFieldType.NUMBER)
                                        .description("사용할 적립금 (원, 0 이상)"),
                                fieldWithPath("addressId").type(JsonFieldType.NUMBER)
                                        .description("배송지 ID. 내 주소록의 배송지여야 한다")),
                        responseHeaders(
                                headerWithName(HttpHeaders.LOCATION).description("만든 주문의 상세 조회 URL")),
                        responseFields(
                                fieldWithPath("orderNumber").type(JsonFieldType.STRING)
                                        .description("주문번호 (`yyyyMMdd-` + 영문 대문자·숫자 8자). 결제에도 이 값을 쓴다"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("주문 상태. 만든 직후에는 `PENDING`(결제 대기)이다"),
                                fieldWithPath("orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                                fieldWithPath("expiresAt").type(JsonFieldType.STRING)
                                        .description("결제 기한. 주문 시각 + 15분이며 이 시각부터 만료다"),
                                fieldWithPath("items").type(JsonFieldType.ARRAY)
                                        .description("주문상품. 요청한 `cartItemIds` 순서 그대로다"),
                                fieldWithPath("items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].skuId").type(JsonFieldType.NUMBER).description("옵션(SKU) ID"),
                                fieldWithPath("items[].productName").type(JsonFieldType.STRING)
                                        .description("주문 시점의 상품명"),
                                fieldWithPath("items[].skuName").type(JsonFieldType.STRING)
                                        .description("주문 시점의 옵션 이름"),
                                fieldWithPath("items[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("주문 시점의 대표 이미지 URL. 이미지가 없었으면 null이다"),
                                fieldWithPath("items[].unitPrice").type(JsonFieldType.NUMBER)
                                        .description("옵션 정가 (원)"),
                                fieldWithPath("items[].unitSalePrice").type(JsonFieldType.NUMBER)
                                        .description("주문 시점의 옵션 판매가 (원)"),
                                fieldWithPath("items[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                                fieldWithPath("items[].lineTotal").type(JsonFieldType.NUMBER)
                                        .description("줄 합계 (원). `unitSalePrice` × `quantity`다"),
                                fieldWithPath("price").type(JsonFieldType.OBJECT)
                                        .description("금액. 같은 요청의 주문서 미리보기와 같은 계산이다"),
                                fieldWithPath("price.itemsPrice").type(JsonFieldType.NUMBER)
                                        .description("정가 합계 (원)"),
                                fieldWithPath("price.saleDiscount").type(JsonFieldType.NUMBER)
                                        .description("세일 할인 금액 (원)"),
                                fieldWithPath("price.itemsSalePrice").type(JsonFieldType.NUMBER)
                                        .description("세일가 합계 (원)"),
                                fieldWithPath("price.couponDiscount").type(JsonFieldType.NUMBER)
                                        .description("쿠폰 할인 금액 (원). 쿠폰을 쓰지 않으면 0이다"),
                                fieldWithPath("price.shippingFee").type(JsonFieldType.NUMBER)
                                        .description("배송비 (원)"),
                                fieldWithPath("price.pointsUsed").type(JsonFieldType.NUMBER)
                                        .description("사용한 적립금 (원)"),
                                fieldWithPath("price.paymentAmount").type(JsonFieldType.NUMBER)
                                        .description("결제할 금액 (원)"),
                                fieldWithPath("couponName").type(JsonFieldType.STRING).optional()
                                        .description("주문 시점의 쿠폰 이름. 쿠폰을 쓰지 않으면 null이다"),
                                fieldWithPath("shipping").type(JsonFieldType.OBJECT)
                                        .description("주문 시점에 주소록에서 복사한 배송지"),
                                fieldWithPath("shipping.recipient").type(JsonFieldType.STRING)
                                        .description("받는 사람"),
                                fieldWithPath("shipping.phone").type(JsonFieldType.STRING).description("연락처"),
                                fieldWithPath("shipping.zipCode").type(JsonFieldType.STRING).description("우편번호"),
                                fieldWithPath("shipping.address1").type(JsonFieldType.STRING).description("주소"),
                                fieldWithPath("shipping.address2").type(JsonFieldType.STRING).optional()
                                        .description("상세 주소. 없으면 null이다"),
                                fieldWithPath("canceledAt").type(JsonFieldType.STRING).optional()
                                        .description("취소 시각. 취소되지 않았으면 null이다"),
                                fieldWithPath("cancelReason").type(JsonFieldType.STRING).optional()
                                        .description("취소 이유(`EXPIRED`: 결제 기한 지남, `REPLACED`: 새 주문으로 대체). "
                                                + "취소되지 않았으면 null이다"))));

        JsonNode created = json(result);
        assertThat(created.get("price")).isEqualTo(previewPrice);
        String orderNumber = created.get("orderNumber").asText();
        assertThat(orderNumber).matches("\\d{8}-[0-9A-Z]{8}");
        assertThat(result.andReturn().getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/members/me/orders/" + orderNumber);
        // 기한은 요청의 now + 15분이고, 주문 시각(created_at)은 저장할 때의 시계라 now보다 조금 늦을 수 있다.
        Duration window = Duration.between(Instant.parse(created.get("orderedAt").asText()),
                Instant.parse(created.get("expiresAt").asText()));
        assertThat(window).isBetween(Duration.ofMinutes(15).minusSeconds(1), Duration.ofMinutes(15));

        Long orderId = (Long) orderRow(orderNumber).get("id");
        assertThat(orderRow(orderNumber).get("status")).isEqualTo("PENDING");
        assertThat(stockOf(lineId)).isEqualTo(8);
        assertThat(memberCoupon(memberCouponId)).containsEntry("status", "USED").containsEntry("used_order_id", orderId);
        assertThat(balance()).isEqualTo(4_000);
        assertThat(jdbcTemplate.queryForMap(
                "select amount, order_id from point_history where member_id = ? and type = 'USE'", memberId))
                .containsEntry("amount", 1_000L).containsEntry("order_id", orderId);
        assertThat(jdbcTemplate.queryForObject("select count(*) from order_item where order_id = ?", Integer.class,
                orderId)).isEqualTo(1);
        // 장바구니 줄은 결제가 승인될 때 지운다.
        assertThat(jdbcTemplate.queryForObject("select count(*) from cart_item where id = ?", Integer.class, lineId))
                .isEqualTo(1);
    }

    @Test
    void 쿠폰과_적립금_없이도_주문한다() throws Exception {
        Long lineId = cartLine("선크림", 10_000, 10, 1, null);

        order("{\"cartItemIds\":[" + lineId + "],\"points\":0,\"addressId\":" + addressId + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price.couponDiscount").value(0))
                .andExpect(jsonPath("$.price.paymentAmount").value(12_500))
                .andExpect(jsonPath("$.couponName").doesNotExist());

        assertThat(stockOf(lineId)).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject("select count(*) from point_history", Integer.class)).isZero();
    }

    @Test
    void 대기_주문이_있으면_이전_주문을_취소하고_같은_쿠폰으로_새로_만든다() throws Exception {
        Long lineId = saleLine();
        Long memberCouponId = newMemberCoupon();
        pointService.grant(memberId, 5_000, "이벤트");
        String first = json(order(body(lineId, memberCouponId, 1_000)).andExpect(status().isCreated()))
                .get("orderNumber").asText();

        String second = json(order(body(lineId, memberCouponId, 1_000)).andExpect(status().isCreated()))
                .get("orderNumber").asText();

        Map<String, Object> old = orderRow(first);
        assertThat(old).containsEntry("status", "CANCELED").containsEntry("cancel_reason", "REPLACED");
        assertThat(old.get("canceled_at")).isNotNull();
        Long newOrderId = (Long) orderRow(second).get("id");
        assertThat(orderRow(second).get("status")).isEqualTo("PENDING");
        // 재고·쿠폰·적립금은 이전 주문에서 되돌리고 새 주문에서 다시 뺐으므로 한 번만 차감된 상태다.
        assertThat(stockOf(lineId)).isEqualTo(8);
        assertThat(memberCoupon(memberCouponId))
                .containsEntry("status", "USED").containsEntry("used_order_id", newOrderId);
        assertThat(balance()).isEqualTo(4_000);
        assertThat(jdbcTemplate.queryForList(
                "select type from point_history where member_id = ? and order_id is not null order by id",
                String.class, memberId)).containsExactly("USE", "RESTORE", "USE");
    }

    @Test
    void 새_주문이_실패하면_이전_대기_주문은_그대로다() throws Exception {
        Long lineId = saleLine();
        Long memberCouponId = newMemberCoupon();
        pointService.grant(memberId, 5_000, "이벤트");
        String first = json(order(body(lineId, memberCouponId, 1_000)).andExpect(status().isCreated()))
                .get("orderNumber").asText();
        Long firstId = (Long) orderRow(first).get("id");

        // 이전 주문을 되돌리면 잔액은 5,000원이라 6,000원은 쓸 수 없다.
        order(body(lineId, memberCouponId, 6_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINTS_NOT_ENOUGH"));

        assertThat(orderRow(first).get("status")).isEqualTo("PENDING");
        assertThat(orderRow(first).get("cancel_reason")).isNull();
        assertThat(memberCoupon(memberCouponId)).containsEntry("status", "USED").containsEntry("used_order_id", firstId);
        assertThat(stockOf(lineId)).isEqualTo(8);
        assertThat(balance()).isEqualTo(4_000);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void 다른_회원의_배송지면_404_ADDRESS_NOT_FOUND() throws Exception {
        Long lineId = saleLine();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        Long othersAddress = address(other);

        order(body("[" + lineId + "]", null, 0, othersAddress))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        order(body("[" + lineId + "]", null, 0, 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        // 배송지 확인이 주문서 검증(줄 ID 중복 확인부터)보다 앞선다.
        order(body("[" + lineId + "," + lineId + "]", null, 0, othersAddress))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();
        assertThat(stockOf(lineId)).isEqualTo(10);
    }

    @Test
    void 다른_회원의_장바구니_줄이면_404() throws Exception {
        Long mine = saleLine();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        Long skuId = jdbcTemplate.queryForObject("select sku_id from cart_item where id = ?", Long.class, mine);
        cartService.add(other, new CartAddRequest(skuId, 1));
        Long othersLine = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class, other);

        order(body("[" + mine + "," + othersLine + "]", null, 0, addressId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        order(body("[999999]", null, 0, addressId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    void 구매할_수_없는_줄이_있으면_409_CART_ITEM_UNAVAILABLE() throws Exception {
        Long ok = saleLine();
        Long soldOut = cartLine("품절될 토너", 10_000, 5, 2, null);
        Long productId = jdbcTemplate.queryForObject(
                "select s.product_id from cart_item c join sku s on s.id = c.sku_id where c.id = ?", Long.class, soldOut);
        Long skuId = jdbcTemplate.queryForObject("select sku_id from cart_item where id = ?", Long.class, soldOut);
        adminSkuService.adjustStock(productId, skuId, new StockAdjustRequest(-5));

        order(body("[" + ok + "," + soldOut + "]", null, 0, addressId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_ITEM_UNAVAILABLE"));
        assertThat(stockOf(ok)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    void 판매_중지_상품이면_409_CART_ITEM_UNAVAILABLE() throws Exception {
        Long lineId = saleLine();
        Long productId = jdbcTemplate.queryForObject(
                "select s.product_id from cart_item c join sku s on s.id = c.sku_id where c.id = ?", Long.class, lineId);
        adminProductService.changeStatus(productId, new ProductStatusRequest(ProductStatus.STOPPED));

        order(body(lineId, null, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_ITEM_UNAVAILABLE"));
        assertThat(stockOf(lineId)).isEqualTo(10);
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        Long lineId = saleLine();
        String fiftyOneDistinct = "[" + LongStream.rangeClosed(1, 51)
                .mapToObj(Long::toString).collect(Collectors.joining(",")) + "]";

        order(body("[" + lineId + "]", null, 0, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        order(body("[]", null, 0, addressId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        order(body(fiftyOneDistinct, null, 0, addressId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        order(body("[" + lineId + "," + lineId + "]", null, 0, addressId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        order(body(lineId, null, -1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(post("/api/members/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1]", null, 0, 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }
}
