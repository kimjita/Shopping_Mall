package com.sprint.mission.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.service.AdminCouponService;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.payment.FakePaymentGateway;
import com.sprint.mission.payment.FakePaymentGateway.CancelCall;
import com.sprint.mission.payment.client.PaymentTimeoutException;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@Import(FakePaymentGateway.Config.class)
class OrderCancelApiTest extends ApiTest {

    private static final String KEY = "tgen_20261020_key1";

    /** 20,000원 x 2 - 쿠폰 3,000원 - 적립금 1,000원이다(배송비 무료). */
    private static final long AMOUNT = 36_000;

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
    AdminCouponService adminCouponService;

    @Autowired
    CouponService couponService;

    @Autowired
    PointService pointService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;
    Long addressId;
    Long orderedLine;
    Long skuId;
    Long memberCouponId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartService.add(memberId, new CartAddRequest(skuId, 2));
        orderedLine = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class,
                memberId);
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 10, null)).id();
        couponService.download(memberId, couponId);
        memberCouponId = jdbcTemplate.queryForObject("select id from member_coupon where member_id = ?", Long.class,
                memberId);
        pointService.grant(memberId, 1_000, "이벤트");
    }

    @AfterEach
    void resetGateway() {
        gateway.reset();
    }

    private String placeOrder() {
        return orderService.create(memberId,
                new OrderCreateRequest(List.of(orderedLine), memberCouponId, 1_000, addressId)).orderNumber();
    }

    /** 주문하고 결제 승인 API로 결제 완료까지 만든 뒤 Fake 호출 기록을 비운다. */
    private String paidOrder() throws Exception {
        String orderNumber = placeOrder();
        mockMvc.perform(post("/api/members/me/orders/{orderNumber}/payment", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"" + KEY + "\",\"amount\":" + AMOUNT + "}"))
                .andExpect(status().isOk());
        gateway.reset();
        return orderNumber;
    }

    private ResultActions cancel(String token, String orderNumber) throws Exception {
        return mockMvc.perform(post("/api/members/me/orders/{orderNumber}/cancel", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions cancel(String orderNumber) throws Exception {
        return cancel(bearer(memberId, Role.USER), orderNumber);
    }

    private String orderStatus(String orderNumber) {
        return jdbcTemplate.queryForObject("select status from orders where order_number = ?", String.class,
                orderNumber);
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject("select status from payment", String.class);
    }

    private int restoreCount() {
        return jdbcTemplate.queryForObject("select count(*) from point_history where type = 'RESTORE'",
                Integer.class);
    }

    private void assertRestored() {
        assertThat(jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("select status from member_coupon where id = ?", String.class,
                memberCouponId)).isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId))
                .isEqualTo(1_000);
        assertThat(restoreCount()).isEqualTo(1);
    }

    @Test
    void 결제_완료_주문을_취소하면_토스_취소_후_모두_되돌린다() throws Exception {
        String orderNumber = paidOrder();

        cancel(orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("USER"))
                .andDo(document("payment/cancel",
                        requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION)
                                .description("access token (`Bearer <token>`)")),
                        pathParameters(parameterWithName("orderNumber").description("취소할 주문번호")),
                        responseFields(
                                fieldWithPath("orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("주문 상태. 취소되면 `CANCELED`다"),
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
                                        .description("결제했던 금액 (원)"),
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
                                fieldWithPath("canceledAt").type(JsonFieldType.STRING).description("취소 시각"),
                                fieldWithPath("cancelReason").type(JsonFieldType.STRING)
                                        .description("취소 이유. 고객이 취소하면 `USER`다"))));

        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELED");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject("select canceled_at is not null from payment", Boolean.class)).isTrue();
        assertRestored();
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "고객 요청"));
    }

    @Test
    void 다시_취소하면_토스를_부르지_않고_200() throws Exception {
        String orderNumber = paidOrder();
        cancel(orderNumber).andExpect(status().isOk());
        gateway.reset();

        cancel(orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("USER"));

        assertThat(gateway.cancels()).isEmpty();
        assertRestored();
    }

    @Test
    void 결제_대기_주문은_409_ORDER_NOT_CANCELABLE() throws Exception {
        String orderNumber = placeOrder();

        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELABLE"));

        assertThat(gateway.cancels()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
    }

    @Test
    void 만료로_취소된_주문은_409_ORDER_NOT_CANCELABLE() throws Exception {
        String orderNumber = placeOrder();
        jdbcTemplate.update("update orders set status = 'CANCELED', cancel_reason = 'EXPIRED', canceled_at = now() "
                + "where order_number = ?", orderNumber);

        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELABLE"));

        assertThat(gateway.cancels()).isEmpty();
    }

    @Test
    void 상품_준비_중_주문은_409() throws Exception {
        String orderNumber = paidOrder();
        jdbcTemplate.update("update orders set status = 'PREPARING' where order_number = ?", orderNumber);

        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELABLE"));

        assertThat(gateway.cancels()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PREPARING");
        assertThat(paymentStatus()).isEqualTo("DONE");
    }

    @Test
    void 토스_취소가_타임아웃이면_502이고_주문은_결제_완료_그대로다() throws Exception {
        String orderNumber = paidOrder();
        gateway.cancelThrows(new PaymentTimeoutException("시간 초과", null));

        cancel(orderNumber)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");
        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(restoreCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId))
                .isEqualTo(8);

        // 토스는 취소했는데 응답만 늦은 경우다. 다시 요청하면 토스가 이미 취소됨을 알려 주고(클라이언트가 성공 처리) 끝까지 처리된다.
        gateway.reset();
        cancel(orderNumber).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));

        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELED");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertRestored();
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "고객 요청"));
    }

    @Test
    void 다른_회원의_주문번호면_404_ORDER_NOT_FOUND() throws Exception {
        String orderNumber = paidOrder();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");

        cancel(bearer(other, Role.USER), orderNumber)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        cancel("20261020-NOTEXIST")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(gateway.cancels()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        String orderNumber = paidOrder();

        mockMvc.perform(post("/api/members/me/orders/{orderNumber}/cancel", orderNumber))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(gateway.cancels()).isEmpty();
    }
}
