package com.sprint.mission.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.service.OrderExpiryScheduler;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.support.MutableClock;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class OrderQueryApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    /** 결제 기한이 지난 주문을 만들려고 시간을 흘려보낸다. */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MutableClock clock;

    @Autowired
    OrderExpiryScheduler orderExpiryScheduler;

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Long memberId;
    Long addressId;
    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        // DB는 마이크로초까지 저장하므로 생성 응답과 조회 응답의 시각이 같게 맞춘다.
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        addressId = address(memberId);
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private String token(Long id) {
        return bearer(id, Role.USER);
    }

    private Long address(Long ownerId) {
        return addressService.create(ownerId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
    }

    /** 옵션 하나짜리 상품을 만들어 장바구니에 담고 cart_item id를 돌려준다. */
    private Long cartLine(Long ownerId, String name, int quantity) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, 10_000, List.of("https://img.example.com/" + name + ".jpg"),
                List.of(new SkuRequest("기본", 0, 100))));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(ownerId, new CartAddRequest(skuId, quantity));
        return jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, ownerId, skuId);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** 장바구니 줄 하나로 주문을 만들고 응답 본문을 돌려준다. 이전 대기 주문은 REPLACED가 된다. */
    private JsonNode placeOrder(Long ownerId, Long ownerAddressId, String productName, int quantity) throws Exception {
        Long lineId = cartLine(ownerId, productName, quantity);
        return placeOrder(ownerId, ownerAddressId, List.of(lineId));
    }

    private JsonNode placeOrder(Long ownerId, Long ownerAddressId, List<Long> lineIds) throws Exception {
        return json(mockMvc.perform(post("/api/members/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, token(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":" + lineIds + ",\"points\":0,\"addressId\":" + ownerAddressId + "}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions list(String token, String... params) throws Exception {
        var request = get("/api/members/me/orders").header(HttpHeaders.AUTHORIZATION, token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    void 내_주문을_최신순으로_페이지_나눠_읽는다() throws Exception {
        // 앞의 두 주문은 새 주문에 밀려 REPLACED가 되고, 마지막 주문만 PENDING이다.
        String first = placeOrder(memberId, addressId, "토너", 1).get("orderNumber").asText();
        String second = placeOrder(memberId, addressId, "에센스", 2).get("orderNumber").asText();
        Long thirdLine = cartLine(memberId, "크림", 1);
        Long thirdLine2 = cartLine(memberId, "선크림", 3);
        String third = placeOrder(memberId, addressId, List.of(thirdLine, thirdLine2)).get("orderNumber").asText();

        JsonNode firstPage = json(list(token(memberId), "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].orderNumber").value(third))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].firstProductName").value("크림"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://img.example.com/크림.jpg"))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.items[0].paymentAmount").value(40_000))
                .andExpect(jsonPath("$.items[1].orderNumber").value(second))
                .andExpect(jsonPath("$.items[1].status").value("CANCELED"))
                .andExpect(jsonPath("$.items[1].itemCount").value(1))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andDo(document("order/list",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("내 주문. 최근 주문이 먼저다"),
                                fieldWithPath("items[].orderNumber").type(JsonFieldType.STRING)
                                        .description("주문번호. 상세 조회에 쓴다"),
                                fieldWithPath("items[].status").type(JsonFieldType.STRING)
                                        .description("주문 상태 (`PENDING` 결제 대기, `CANCELED` 취소 등)"),
                                fieldWithPath("items[].orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                                fieldWithPath("items[].firstProductName").type(JsonFieldType.STRING)
                                        .description("첫 번째 주문상품의 상품명. 주문 시점의 값이다"),
                                fieldWithPath("items[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                                        .description("첫 번째 주문상품의 대표 이미지 URL. 이미지가 없었으면 null이다"),
                                fieldWithPath("items[].itemCount").type(JsonFieldType.NUMBER)
                                        .description("주문상품 줄 수. 수량의 합이 아니다"),
                                fieldWithPath("items[].paymentAmount").type(JsonFieldType.NUMBER)
                                        .description("결제할 금액 (원)"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`")))));

        String cursor = firstPage.get("nextCursor").asText();
        list(token(memberId), "size", "2", "cursor", cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(first))
                .andExpect(jsonPath("$.items[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.items[0].firstProductName").value("토너"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 주문이_없으면_빈_목록이다() throws Exception {
        list(token(memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 다른_회원의_주문은_목록에_나오지_않는다() throws Exception {
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        placeOrder(other, address(other), "토너", 1);
        placeOrder(memberId, addressId, "에센스", 1);

        list(token(memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].firstProductName").value("에센스"));
    }

    @Test
    void 목록_쿼리_수는_주문_수와_상관없다() throws Exception {
        placeOrder(memberId, addressId, "상품0", 1);
        placeOrder(memberId, addressId, "상품1", 2);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        list(token(memberId)).andExpect(jsonPath("$.items.length()").value(2));
        long withTwo = statistics.getPrepareStatementCount();

        for (int i = 2; i < 10; i++) {
            placeOrder(memberId, addressId, "상품" + i, 1);
        }
        statistics.clear();
        list(token(memberId)).andExpect(jsonPath("$.items.length()").value(10));
        long withTen = statistics.getPrepareStatementCount();

        assertThat(withTwo).isPositive();
        assertThat(withTen).isEqualTo(withTwo);
    }

    @Test
    void 주문_상세를_읽는다() throws Exception {
        Long line1 = cartLine(memberId, "토너", 2);
        Long line2 = cartLine(memberId, "에센스", 1);
        JsonNode created = placeOrder(memberId, addressId, List.of(line1, line2));
        String orderNumber = created.get("orderNumber").asText();

        JsonNode detail = parse(mockMvc.perform(get("/api/members/me/orders/{orderNumber}", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, token(memberId)))
                .andExpect(status().isOk())
                .andDo(document("order/detail",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("orderNumber").description("주문번호")),
                        responseFields(detailFields())))
                .andReturn().getResponse().getContentAsString());

        assertThat(detail).isEqualTo(created);
        assertThat(detail.get("items").get(0).get("productName").asText()).isEqualTo("토너");
        assertThat(detail.get("items").get(1).get("productName").asText()).isEqualTo("에센스");
    }

    /** 회원용 주문 상세 응답 필드다. 상세·구매확정이 같은 모양을 돌려준다. */
    static FieldDescriptor[] detailFields() {
        return new FieldDescriptor[] {
                fieldWithPath("orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                fieldWithPath("status").type(JsonFieldType.STRING)
                        .description("주문 상태 (`PENDING` 결제 대기, `CANCELED` 취소 등)"),
                fieldWithPath("orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                fieldWithPath("expiresAt").type(JsonFieldType.STRING)
                        .description("결제 기한. 주문 시각 + 15분이며 이 시각부터 만료다"),
                fieldWithPath("items").type(JsonFieldType.ARRAY).description("주문상품. 주문한 순서 그대로다"),
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
                fieldWithPath("price").type(JsonFieldType.OBJECT).description("금액"),
                fieldWithPath("price.itemsPrice").type(JsonFieldType.NUMBER)
                        .description("정가 합계 (원)"),
                fieldWithPath("price.saleDiscount").type(JsonFieldType.NUMBER)
                        .description("세일 할인 금액 (원)"),
                fieldWithPath("price.itemsSalePrice").type(JsonFieldType.NUMBER)
                        .description("세일가 합계 (원)"),
                fieldWithPath("price.couponDiscount").type(JsonFieldType.NUMBER)
                        .description("쿠폰 할인 금액 (원). 쿠폰을 쓰지 않았으면 0이다"),
                fieldWithPath("price.shippingFee").type(JsonFieldType.NUMBER)
                        .description("배송비 (원)"),
                fieldWithPath("price.pointsUsed").type(JsonFieldType.NUMBER)
                        .description("사용한 적립금 (원)"),
                fieldWithPath("price.paymentAmount").type(JsonFieldType.NUMBER)
                        .description("결제할 금액 (원)"),
                fieldWithPath("couponName").type(JsonFieldType.STRING).optional()
                        .description("주문 시점의 쿠폰 이름. 쿠폰을 쓰지 않았으면 null이다"),
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
                        .description("취소 이유(`EXPIRED`: 결제 기한 지남, `REPLACED`: 새 주문으로 대체, "
                                + "`USER`: 고객 취소, `ADMIN`: 판매자 취소). 취소되지 않았으면 null이다"),
                fieldWithPath("deliveredAt").type(JsonFieldType.STRING).optional()
                        .description("배송 완료 시각. 배송 완료 전이면 null이다"),
                fieldWithPath("confirmedAt").type(JsonFieldType.STRING).optional()
                        .description("구매확정 시각. 구매확정 전이면 null이다")
        };
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 취소된_주문은_취소_시각과_사유가_나온다() throws Exception {
        String replaced = placeOrder(memberId, addressId, "토너", 1).get("orderNumber").asText();
        String expired = placeOrder(memberId, addressId, "에센스", 1).get("orderNumber").asText();

        // 두 번째 주문이 만료되기 전까지는 PENDING이다.
        detail(token(memberId), expired)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.canceledAt").doesNotExist())
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
        detail(token(memberId), replaced)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("REPLACED"))
                .andExpect(jsonPath("$.canceledAt").isString());

        clock.advance(Order.PAYMENT_WINDOW);
        assertThat(orderExpiryScheduler.expireDue()).isEqualTo(1);

        detail(token(memberId), expired)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("EXPIRED"))
                .andExpect(jsonPath("$.canceledAt").value(clock.instant().toString()));
    }

    @Test
    void 다른_회원의_주문번호면_404_ORDER_NOT_FOUND() throws Exception {
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");
        String othersOrder = placeOrder(other, address(other), "토너", 1).get("orderNumber").asText();

        detail(token(memberId), othersOrder)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        // 없는 번호와 응답이 같아 남의 주문 존재 여부를 드러내지 않는다.
        detail(token(memberId), "20261019-NOSUCHXX")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(get("/api/members/me/orders/{orderNumber}", othersOrder))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_커서나_size면_400() throws Exception {
        String token = token(memberId);
        list(token, "cursor", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list(token, "cursor", "0").andExpect(status().isBadRequest());
        list(token, "cursor", "9".repeat(20)).andExpect(status().isBadRequest());
        list(token, "cursor", "1".repeat(21)).andExpect(status().isBadRequest());
        list(token, "size", "0").andExpect(status().isBadRequest());
        list(token, "size", "51").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/members/me/orders")).andExpect(status().isUnauthorized());
    }

    private ResultActions detail(String token, String orderNumber) throws Exception {
        return mockMvc.perform(get("/api/members/me/orders/{orderNumber}", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token));
    }
}
