package com.sprint.mission.order.controller;

import static com.sprint.mission.order.controller.AdminOrderQueryApiTest.detailFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
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
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@Import(FakePaymentGateway.Config.class)
class AdminOrderCommandApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

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
    Long skuId;
    Long memberCouponId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
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

    private String admin() {
        return bearer(1L, Role.ADMIN);
    }

    /** 쿠폰·적립금을 쓴 주문을 결제 승인 API로 결제 완료까지 만든 뒤 Fake 호출 기록을 비운다. */
    private String paidOrder() throws Exception {
        Long addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        cartService.add(memberId, new CartAddRequest(skuId, 2));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class,
                memberId);
        String orderNumber = orderService.create(memberId,
                new OrderCreateRequest(List.of(lineId), memberCouponId, 1_000, addressId)).orderNumber();
        mockMvc.perform(post("/api/members/me/orders/{orderNumber}/payment", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"" + KEY + "\",\"amount\":" + AMOUNT + "}"))
                .andExpect(status().isOk());
        gateway.reset();
        return orderNumber;
    }

    private ResultActions changeStatus(String token, String orderNumber, String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/orders/{orderNumber}/status", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions changeStatus(String orderNumber, String status) throws Exception {
        return changeStatus(admin(), orderNumber, status);
    }

    private ResultActions cancel(String token, String orderNumber) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/{orderNumber}/cancel", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions cancel(String orderNumber) throws Exception {
        return cancel(admin(), orderNumber);
    }

    private void setStatus(String orderNumber, String status) {
        jdbcTemplate.update("update orders set status = ? where order_number = ?", status, orderNumber);
    }

    private String orderStatus(String orderNumber) {
        return jdbcTemplate.queryForObject("select status from orders where order_number = ?", String.class,
                orderNumber);
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject("select status from payment", String.class);
    }

    private int stock() {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    private int restoreCount() {
        return jdbcTemplate.queryForObject("select count(*) from point_history where type = 'RESTORE'",
                Integer.class);
    }

    private void assertRestored() {
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("select status from member_coupon where id = ?", String.class,
                memberCouponId)).isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId))
                .isEqualTo(1_000);
        assertThat(restoreCount()).isEqualTo(1);
    }

    @Test
    void 결제_완료부터_배송_완료까지_바꾼다() throws Exception {
        String orderNumber = paidOrder();

        changeStatus(orderNumber, "PREPARING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.order.status").value("PREPARING"))
                .andExpect(jsonPath("$.memberEmail").value("user@mall.com"))
                .andExpect(jsonPath("$.payment.paymentKey").value(KEY))
                .andExpect(jsonPath("$.order.deliveredAt").doesNotExist())
                .andDo(document("admin-order/status",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("orderNumber").description("주문번호")),
                        requestFields(fieldWithPath("status").type(JsonFieldType.STRING)
                                .description("바꿀 상태 (`PREPARING`, `SHIPPING`, `DELIVERED`). 한 단계씩만 바꿀 수 있다")),
                        responseFields(detailFields())));
        changeStatus(orderNumber, "SHIPPING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("SHIPPING"))
                .andExpect(jsonPath("$.order.deliveredAt").doesNotExist());
        changeStatus(orderNumber, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("DELIVERED"))
                .andExpect(jsonPath("$.order.deliveredAt").isString());

        assertThat(orderStatus(orderNumber)).isEqualTo("DELIVERED");
        assertThat(jdbcTemplate.queryForObject("select delivered_at is not null from orders", Boolean.class))
                .isTrue();
    }

    @Test
    void 허용하지_않는_대상_상태는_400이고_그대로다() throws Exception {
        String orderNumber = paidOrder();

        for (String target : List.of("CANCELED", "CONFIRMED", "PAID", "PENDING", "SHIPPED")) {
            changeStatus(orderNumber, target)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        mockMvc.perform(patch("/api/admin/orders/{orderNumber}/status", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");
        assertThat(gateway.cancels()).isEmpty();
        assertThat(restoreCount()).isZero();
    }

    @Test
    void 건너뛰거나_같은_상태로_바꾸면_409() throws Exception {
        String orderNumber = paidOrder();

        changeStatus(orderNumber, "SHIPPING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        changeStatus(orderNumber, "DELIVERED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");

        changeStatus(orderNumber, "PREPARING").andExpect(status().isOk());
        changeStatus(orderNumber, "PREPARING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PREPARING");

        // 취소된 주문은 어느 단계로도 바꿀 수 없다.
        setStatus(orderNumber, "CANCELED");
        changeStatus(orderNumber, "SHIPPING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject("select delivered_at is null from orders", Boolean.class)).isTrue();
    }

    @Test
    void 상품_준비_중_주문을_관리자가_취소하면_토스_취소_후_모두_되돌린다() throws Exception {
        String orderNumber = paidOrder();
        changeStatus(orderNumber, "PREPARING").andExpect(status().isOk());
        assertThat(stock()).isEqualTo(8);

        cancel(orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.order.status").value("CANCELED"))
                .andExpect(jsonPath("$.order.cancelReason").value("ADMIN"))
                .andExpect(jsonPath("$.order.canceledAt").isString())
                .andExpect(jsonPath("$.payment.canceledAt").isString())
                .andDo(document("admin-order/cancel",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("orderNumber").description("취소할 주문번호")),
                        responseFields(detailFields())));

        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELED");
        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertRestored();
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "판매자 취소"));
    }

    @Test
    void 결제_완료_주문도_관리자가_취소한다() throws Exception {
        String orderNumber = paidOrder();

        cancel(orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELED"))
                .andExpect(jsonPath("$.order.cancelReason").value("ADMIN"));

        assertThat(paymentStatus()).isEqualTo("CANCELED");
        assertRestored();
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "판매자 취소"));
    }

    @Test
    void 다시_취소하면_토스를_부르지_않고_200() throws Exception {
        String orderNumber = paidOrder();
        cancel(orderNumber).andExpect(status().isOk());
        gateway.reset();

        cancel(orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELED"))
                .andExpect(jsonPath("$.order.cancelReason").value("ADMIN"));

        assertThat(gateway.cancels()).isEmpty();
        assertRestored();
    }

    @Test
    void 배송_중_주문은_취소_409() throws Exception {
        String orderNumber = paidOrder();
        setStatus(orderNumber, "SHIPPING");

        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELABLE"));

        assertThat(gateway.cancels()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("SHIPPING");
        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(restoreCount()).isZero();
    }

    @Test
    void 고객이_취소한_주문은_관리자_취소_409() throws Exception {
        String orderNumber = paidOrder();
        mockMvc.perform(post("/api/members/me/orders/{orderNumber}/cancel", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER)))
                .andExpect(status().isOk());
        gateway.reset();

        cancel(orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELABLE"));

        assertThat(gateway.cancels()).isEmpty();
        assertRestored();
    }

    @Test
    void 토스_취소가_실패하면_502이고_그대로다() throws Exception {
        String orderNumber = paidOrder();
        changeStatus(orderNumber, "PREPARING").andExpect(status().isOk());
        gateway.cancelThrows(new PaymentTimeoutException("시간 초과", null));

        cancel(orderNumber)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        assertThat(orderStatus(orderNumber)).isEqualTo("PREPARING");
        assertThat(paymentStatus()).isEqualTo("DONE");
        assertThat(restoreCount()).isZero();
        assertThat(stock()).isEqualTo(8);
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "판매자 취소"));
    }

    @Test
    void 없는_주문번호면_404() throws Exception {
        changeStatus("20261020-NOTEXIST", "PREPARING")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        cancel("20261020-NOTEXIST")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(gateway.cancels()).isEmpty();
    }

    @Test
    void 일반_회원은_403() throws Exception {
        String orderNumber = paidOrder();
        String user = bearer(memberId, Role.USER);

        changeStatus(user, orderNumber, "PREPARING").andExpect(status().isForbidden());
        cancel(user, orderNumber).andExpect(status().isForbidden());

        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");
        assertThat(gateway.cancels()).isEmpty();
    }
}
