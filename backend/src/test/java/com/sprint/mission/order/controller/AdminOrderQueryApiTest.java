package com.sprint.mission.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
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
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.payment.FakePaymentGateway;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@Import(FakePaymentGateway.Config.class)
class AdminOrderQueryApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    OrderService orderService;

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

    Long brandId;
    Long categoryId;
    int sequence;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    @AfterEach
    void resetGateway() {
        gateway.reset();
    }

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    private Long newMember(String email) {
        return memberService.signup(email, "password1", "홍길동", "01012345678");
    }

    /** 새 회원이 상품 하나를 주문한다. 회원마다 결제 대기 주문이 하나뿐이라 서로 밀어내지 않는다. */
    private String placeOrder(Long memberId, String productName, int quantity, String status) {
        Long addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, productName,
                null, "설명이다.", null, 10_000, List.of("https://img.example.com/" + productName + ".jpg"),
                List.of(new SkuRequest("기본", 0, 100))));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(memberId, new CartAddRequest(skuId, quantity));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, skuId);
        String orderNumber = orderService
                .create(memberId, new OrderCreateRequest(List.of(lineId), null, 0, addressId)).orderNumber();
        jdbcTemplate.update("update orders set status = ? where order_number = ?", status, orderNumber);
        return orderNumber;
    }

    private ResultActions list(String token, String... params) throws Exception {
        var request = get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions detail(String token, String orderNumber) throws Exception {
        return mockMvc.perform(get("/api/admin/orders/{orderNumber}", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    /** 관리자 주문 상세 응답 필드다. 상세·상태 변경·취소가 같은 모양을 돌려준다. */
    static FieldDescriptor[] detailFields() {
        return new FieldDescriptor[] {
                fieldWithPath("order").type(JsonFieldType.OBJECT)
                        .description("주문 본문. 회원용 주문 상세와 같은 모양이다"),
                fieldWithPath("order.orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                fieldWithPath("order.status").type(JsonFieldType.STRING).description("주문 상태"),
                fieldWithPath("order.orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                fieldWithPath("order.expiresAt").type(JsonFieldType.STRING)
                        .description("결제 기한. 주문 시각 + 15분이며 이 시각부터 만료다"),
                fieldWithPath("order.items").type(JsonFieldType.ARRAY)
                        .description("주문상품. 주문한 순서 그대로다"),
                fieldWithPath("order.items[].productId").type(JsonFieldType.NUMBER)
                        .description("상품 ID"),
                fieldWithPath("order.items[].skuId").type(JsonFieldType.NUMBER)
                        .description("옵션(SKU) ID"),
                fieldWithPath("order.items[].productName").type(JsonFieldType.STRING)
                        .description("주문 시점의 상품명"),
                fieldWithPath("order.items[].skuName").type(JsonFieldType.STRING)
                        .description("주문 시점의 옵션 이름"),
                fieldWithPath("order.items[].thumbnailUrl").type(JsonFieldType.STRING).optional()
                        .description("주문 시점의 대표 이미지 URL. 이미지가 없었으면 null이다"),
                fieldWithPath("order.items[].unitPrice").type(JsonFieldType.NUMBER)
                        .description("옵션 정가 (원)"),
                fieldWithPath("order.items[].unitSalePrice").type(JsonFieldType.NUMBER)
                        .description("주문 시점의 옵션 판매가 (원)"),
                fieldWithPath("order.items[].quantity").type(JsonFieldType.NUMBER)
                        .description("수량"),
                fieldWithPath("order.items[].lineTotal").type(JsonFieldType.NUMBER)
                        .description("줄 합계 (원). `unitSalePrice` × `quantity`다"),
                fieldWithPath("order.price").type(JsonFieldType.OBJECT).description("금액"),
                fieldWithPath("order.price.itemsPrice").type(JsonFieldType.NUMBER)
                        .description("정가 합계 (원)"),
                fieldWithPath("order.price.saleDiscount").type(JsonFieldType.NUMBER)
                        .description("세일 할인 금액 (원)"),
                fieldWithPath("order.price.itemsSalePrice").type(JsonFieldType.NUMBER)
                        .description("세일가 합계 (원)"),
                fieldWithPath("order.price.couponDiscount").type(JsonFieldType.NUMBER)
                        .description("쿠폰 할인 금액 (원). 쿠폰을 쓰지 않았으면 0이다"),
                fieldWithPath("order.price.shippingFee").type(JsonFieldType.NUMBER)
                        .description("배송비 (원)"),
                fieldWithPath("order.price.pointsUsed").type(JsonFieldType.NUMBER)
                        .description("사용한 적립금 (원)"),
                fieldWithPath("order.price.paymentAmount").type(JsonFieldType.NUMBER)
                        .description("결제할 금액 (원)"),
                fieldWithPath("order.couponName").type(JsonFieldType.STRING).optional()
                        .description("주문 시점의 쿠폰 이름. 쿠폰을 쓰지 않았으면 null이다"),
                fieldWithPath("order.shipping").type(JsonFieldType.OBJECT)
                        .description("주문 시점에 주소록에서 복사한 배송지"),
                fieldWithPath("order.shipping.recipient").type(JsonFieldType.STRING)
                        .description("받는 사람"),
                fieldWithPath("order.shipping.phone").type(JsonFieldType.STRING)
                        .description("연락처"),
                fieldWithPath("order.shipping.zipCode").type(JsonFieldType.STRING)
                        .description("우편번호"),
                fieldWithPath("order.shipping.address1").type(JsonFieldType.STRING)
                        .description("주소"),
                fieldWithPath("order.shipping.address2").type(JsonFieldType.STRING).optional()
                        .description("상세 주소. 없으면 null이다"),
                fieldWithPath("order.canceledAt").type(JsonFieldType.STRING).optional()
                        .description("취소 시각. 취소되지 않았으면 null이다"),
                fieldWithPath("order.cancelReason").type(JsonFieldType.STRING).optional()
                        .description("취소 이유(`EXPIRED`, `REPLACED`, `USER`, `ADMIN`). "
                                + "취소되지 않았으면 null이다"),
                fieldWithPath("order.deliveredAt").type(JsonFieldType.STRING).optional()
                        .description("배송 완료 시각. 배송 완료 전이면 null이다"),
                fieldWithPath("order.confirmedAt").type(JsonFieldType.STRING).optional()
                        .description("구매확정 시각. 구매확정 전이면 null이다"),
                fieldWithPath("memberId").type(JsonFieldType.NUMBER).description("주문자 회원 ID"),
                fieldWithPath("memberEmail").type(JsonFieldType.STRING).description("주문자 이메일"),
                fieldWithPath("payment").type(JsonFieldType.OBJECT).optional()
                        .description("결제 정보. 결제 전 주문이면 null이다"),
                fieldWithPath("payment.paymentKey").type(JsonFieldType.STRING)
                        .description("토스 결제 키"),
                fieldWithPath("payment.method").type(JsonFieldType.STRING).description("결제수단"),
                fieldWithPath("payment.amount").type(JsonFieldType.NUMBER).description("결제 금액 (원)"),
                fieldWithPath("payment.approvedAt").type(JsonFieldType.STRING)
                        .description("결제 승인 시각"),
                fieldWithPath("payment.canceledAt").type(JsonFieldType.STRING).optional()
                        .description("결제 취소 시각. 취소되지 않았으면 null이다")
        };
    }

    @Test
    void 상태로_걸러_최신순으로_읽는다() throws Exception {
        Long first = newMember("first@mall.com");
        Long second = newMember("second@mall.com");
        Long third = newMember("third@mall.com");
        String paid1 = placeOrder(first, "토너", 1, "PAID");
        placeOrder(second, "에센스", 1, "PENDING");
        String paid2 = placeOrder(third, "크림", 2, "PAID");

        JsonNode firstPage = parse(list(admin(), "status", "PAID", "size", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(paid2))
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].memberId").value(third))
                .andExpect(jsonPath("$.items[0].memberEmail").value("third@mall.com"))
                .andExpect(jsonPath("$.items[0].firstProductName").value("크림"))
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].paymentAmount").value(20_000))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andDo(document("admin-order/list",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("status").optional()
                                        .description("주문 상태로 거른다 (`PENDING`, `PAID`, `PREPARING`, `SHIPPING`, "
                                                + "`DELIVERED`, `CONFIRMED`, `CANCELED`). 없으면 모든 상태다"),
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("주문. 최근 주문이 먼저다"),
                                fieldWithPath("items[].orderNumber").type(JsonFieldType.STRING)
                                        .description("주문번호. 상세 조회에 쓴다"),
                                fieldWithPath("items[].status").type(JsonFieldType.STRING).description("주문 상태"),
                                fieldWithPath("items[].memberId").type(JsonFieldType.NUMBER).description("주문자 회원 ID"),
                                fieldWithPath("items[].memberEmail").type(JsonFieldType.STRING)
                                        .description("주문자 이메일"),
                                fieldWithPath("items[].orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                                fieldWithPath("items[].firstProductName").type(JsonFieldType.STRING)
                                        .description("첫 번째 주문상품의 상품명. 주문 시점의 값이다"),
                                fieldWithPath("items[].itemCount").type(JsonFieldType.NUMBER)
                                        .description("주문상품 줄 수. 수량의 합이 아니다"),
                                fieldWithPath("items[].paymentAmount").type(JsonFieldType.NUMBER)
                                        .description("결제할 금액 (원)"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`")))));

        list(admin(), "status", "PAID", "size", "1", "cursor", firstPage.get("nextCursor").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(paid1))
                .andExpect(jsonPath("$.items[0].memberEmail").value("first@mall.com"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        // 상태를 주지 않으면 모든 상태가 최신순으로 나온다.
        list(admin()).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].orderNumber").value(paid2))
                .andExpect(jsonPath("$.items[1].status").value("PENDING"))
                .andExpect(jsonPath("$.items[2].orderNumber").value(paid1));
        list(admin(), "status", "SHIPPING").andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 목록_쿼리_수는_주문_수와_상관없다() throws Exception {
        for (int i = 0; i < 2; i++) {
            placeOrder(newMember("m" + sequence++ + "@mall.com"), "상품" + sequence, 1, "PAID");
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        list(admin()).andExpect(jsonPath("$.items.length()").value(2));
        long withTwo = statistics.getPrepareStatementCount();

        for (int i = 2; i < 10; i++) {
            placeOrder(newMember("m" + sequence++ + "@mall.com"), "상품" + sequence, 1, "PAID");
        }
        statistics.clear();
        list(admin()).andExpect(jsonPath("$.items.length()").value(10));
        long withTen = statistics.getPrepareStatementCount();

        assertThat(withTwo).isPositive();
        assertThat(withTen).isEqualTo(withTwo);
    }

    @Test
    void 상세에_주문자와_결제_정보가_나온다() throws Exception {
        Long buyer = newMember("buyer@mall.com");
        String orderNumber = placeOrder(buyer, "토너", 2, "PENDING");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/members/me/orders/{orderNumber}/payment", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"tgen_admin_key\",\"amount\":20000}"))
                .andExpect(status().isOk());

        detail(admin(), orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.order.status").value("PAID"))
                .andExpect(jsonPath("$.order.items[0].productName").value("토너"))
                .andExpect(jsonPath("$.memberId").value(buyer))
                .andExpect(jsonPath("$.memberEmail").value("buyer@mall.com"))
                .andExpect(jsonPath("$.payment.paymentKey").value("tgen_admin_key"))
                .andExpect(jsonPath("$.payment.method").value("카드"))
                .andExpect(jsonPath("$.payment.amount").value(20_000))
                .andExpect(jsonPath("$.payment.approvedAt").isString())
                .andExpect(jsonPath("$.payment.canceledAt").doesNotExist())
                .andDo(document("admin-order/detail",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("orderNumber").description("주문번호")),
                        responseFields(detailFields())));

        // 결제 전 주문은 payment가 null이다.
        String pending = placeOrder(newMember("other@mall.com"), "에센스", 1, "PENDING");
        detail(admin(), pending)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("PENDING"))
                .andExpect(jsonPath("$.memberEmail").value("other@mall.com"))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void 없는_주문번호면_404() throws Exception {
        detail(admin(), "20261019-NOSUCHXX")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 잘못된_상태_필터는_400() throws Exception {
        list(admin(), "status", "SHIPPED").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list(admin(), "cursor", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        list(admin(), "cursor", "1".repeat(21)).andExpect(status().isBadRequest());
        list(admin(), "size", "0").andExpect(status().isBadRequest());
        list(admin(), "size", "51").andExpect(status().isBadRequest());
    }

    @Test
    void 일반_회원은_403() throws Exception {
        String user = bearer(newMember("user@mall.com"), Role.USER);
        list(user).andExpect(status().isForbidden());
        detail(user, "20261019-NOSUCHXX").andExpect(status().isForbidden());
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders/{orderNumber}", "20261019-NOSUCHXX"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode parse(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
