package com.sprint.mission.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
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
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.service.OrderExpiryScheduler;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.payment.FakePaymentGateway;
import com.sprint.mission.payment.FakePaymentGateway.CancelCall;
import com.sprint.mission.payment.FakePaymentGateway.ConfirmCall;
import com.sprint.mission.payment.client.PaymentGatewayException;
import com.sprint.mission.payment.client.PaymentResult;
import com.sprint.mission.payment.client.PaymentTimeoutException;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.support.MutableClock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@Import(FakePaymentGateway.Config.class)
class PaymentConfirmApiTest extends ApiTest {

    /** 결제 기한을 넘기려고 시간을 흘려보낸다. */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    private static final String KEY = "tgen_20261020_key1";

    /** 20,000원 × 2 - 쿠폰 3,000원 - 적립금 1,000원이다(배송비 무료). */
    private static final long AMOUNT = 36_000;

    @Autowired
    MutableClock clock;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    OrderService orderService;

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
    Long otherLine;
    Long skuId;
    Long memberCouponId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        skuId = sku(brandId, categoryId, "독도 토너");
        Long otherSku = sku(brandId, categoryId, "자작나무 크림");
        orderedLine = cartLine(skuId, 2);
        otherLine = cartLine(otherSku, 1);
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

    /** 20,000원짜리 옵션 하나의 상품이다(재고 10). */
    private Long sku(Long brandId, Long categoryId, String name) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        return jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    private Long cartLine(Long sku, int quantity) {
        cartService.add(memberId, new CartAddRequest(sku, quantity));
        return jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, sku);
    }

    /** 첫 장바구니 줄만 쿠폰·적립금 1,000원과 함께 주문한다. */
    private String placeOrder() {
        return orderService.create(memberId,
                new OrderCreateRequest(List.of(orderedLine), memberCouponId, 1_000, addressId)).orderNumber();
    }

    private ResultActions confirm(String token, String orderNumber, String paymentKey, long amount) throws Exception {
        return mockMvc.perform(post("/api/members/me/orders/{orderNumber}/payment", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentKey\":\"" + paymentKey + "\",\"amount\":" + amount + "}"));
    }

    private ResultActions confirm(String orderNumber, String paymentKey, long amount) throws Exception {
        return confirm(bearer(memberId, Role.USER), orderNumber, paymentKey, amount);
    }

    private String orderStatus(String orderNumber) {
        return jdbcTemplate.queryForObject("select status from orders where order_number = ?", String.class,
                orderNumber);
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("select count(*) from payment", Integer.class);
    }

    private boolean cartHas(Long lineId) {
        return jdbcTemplate.queryForObject("select count(*) from cart_item where id = ?", Integer.class, lineId) == 1;
    }

    /** 주문이 차감한 재고·쿠폰·적립금이 그대로인지 본다. */
    private void assertStillDeducted(String orderNumber) {
        Long orderId = jdbcTemplate.queryForObject("select id from orders where order_number = ?", Long.class,
                orderNumber);
        assertThat(jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForMap("select status, used_order_id from member_coupon where id = ?",
                memberCouponId)).containsEntry("status", "USED").containsEntry("used_order_id", orderId);
        assertThat(jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId))
                .isZero();
    }

    @Test
    void 금액이_맞으면_승인하고_결제_완료가_된다() throws Exception {
        String orderNumber = placeOrder();

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.price.paymentAmount").value(AMOUNT))
                .andDo(document("payment/confirm",
                        requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION)
                                .description("access token (`Bearer <token>`)")),
                        pathParameters(parameterWithName("orderNumber").description("결제할 주문번호")),
                        requestFields(
                                fieldWithPath("paymentKey").type(JsonFieldType.STRING)
                                        .description("토스 결제창이 successUrl로 넘긴 `paymentKey` (200자 이하)"),
                                fieldWithPath("amount").type(JsonFieldType.NUMBER)
                                        .description("토스 결제창이 successUrl로 넘긴 `amount` (원, 1 이상). "
                                                + "주문의 결제할 금액과 같아야 한다")),
                        responseFields(
                                fieldWithPath("orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("주문 상태. 승인되면 `PAID`(결제 완료)다"),
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
                                        .description("결제한 금액 (원)"),
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
                                        .description("취소 시각. 결제 완료 주문이면 null이다"),
                                fieldWithPath("cancelReason").type(JsonFieldType.STRING).optional()
                                        .description("취소 이유. 결제 완료 주문이면 null이다"),
                                fieldWithPath("deliveredAt").type(JsonFieldType.STRING).optional()
                                        .description("배송 완료 시각. 배송 완료 전이면 null이다"),
                                fieldWithPath("confirmedAt").type(JsonFieldType.STRING).optional()
                                        .description("구매확정 시각. 구매확정 전이면 null이다"))));

        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");
        Long orderId = jdbcTemplate.queryForObject("select id from orders where order_number = ?", Long.class,
                orderNumber);
        Map<String, Object> payment = jdbcTemplate.queryForMap("select * from payment");
        assertThat(payment).containsEntry("order_id", orderId).containsEntry("payment_key", KEY)
                .containsEntry("amount", AMOUNT).containsEntry("method", "카드").containsEntry("status", "DONE");
        assertThat(cartHas(orderedLine)).isFalse();
        assertThat(cartHas(otherLine)).isTrue();
        assertThat(gateway.confirms()).containsExactly(new ConfirmCall(KEY, orderNumber, AMOUNT));
        assertThat(gateway.cancels()).isEmpty();
        assertStillDeducted(orderNumber);
    }

    @Test
    void 금액이_다르면_토스를_부르지_않고_400() throws Exception {
        String orderNumber = placeOrder();

        confirm(orderNumber, KEY, AMOUNT - 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(gateway.confirms()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
        assertThat(cartHas(orderedLine)).isTrue();
    }

    @Test
    void 기한이_지난_주문은_토스를_부르지_않고_409() throws Exception {
        String orderNumber = placeOrder();
        // 기한 시각 정각부터 만료다. 스케줄러가 아직 돌지 않아 주문은 PENDING이다.
        clock.advance(Order.PAYMENT_WINDOW);

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));

        assertThat(gateway.confirms()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
    }

    @Test
    void 대체되거나_만료된_주문은_409() throws Exception {
        String replaced = placeOrder();
        String expired = placeOrder();
        clock.advance(Order.PAYMENT_WINDOW);
        orderExpiryScheduler.expireDue();
        assertThat(orderStatus(replaced)).isEqualTo("CANCELED");
        assertThat(orderStatus(expired)).isEqualTo("CANCELED");

        confirm(replaced, KEY, AMOUNT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
        confirm(expired, KEY, AMOUNT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));

        assertThat(gateway.confirms()).isEmpty();
    }

    @Test
    void 같은_paymentKey로_다시_승인하면_토스를_부르지_않고_200() throws Exception {
        String orderNumber = placeOrder();
        confirm(orderNumber, KEY, AMOUNT).andExpect(status().isOk());

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(gateway.confirms()).hasSize(1);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 다른_paymentKey면_409() throws Exception {
        String orderNumber = placeOrder();
        confirm(orderNumber, KEY, AMOUNT).andExpect(status().isOk());

        confirm(orderNumber, "tgen_20261020_key2", AMOUNT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));

        assertThat(gateway.confirms()).hasSize(1);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 토스가_거절하면_400_PAYMENT_REJECTED이고_주문은_대기_그대로다() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmThrows(new PaymentGatewayException(400, "REJECT_CARD_COMPANY", "카드사에서 거절했습니다."));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));

        // 4xx에 토스 코드가 있으면 확실한 거절이라 조회하지 않는다.
        assertThat(gateway.finds()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
        assertThat(paymentCount()).isZero();
        assertThat(gateway.cancels()).isEmpty();
        assertThat(cartHas(orderedLine)).isTrue();
    }

    @Test
    void 입금_대기_결제는_토스_취소_후_400_PAYMENT_METHOD_NOT_SUPPORTED() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmReturns(new PaymentResult(KEY, orderNumber, "WAITING_FOR_DEPOSIT", AMOUNT, "가상계좌", null));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_SUPPORTED"));

        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "주문 처리 실패"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
        assertThat(paymentCount()).isZero();
    }

    @Test
    void 토스_승인_금액이_다르면_토스_취소_후_400() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmReturns(FakePaymentGateway.done(KEY, orderNumber, AMOUNT - 1));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "주문 처리 실패"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
    }

    @Test
    void 토스_승인_후_저장이_실패하면_토스_취소로_보상한다() throws Exception {
        String orderNumber = placeOrder();
        // 다른 주문의 결제 기록이 같은 payment_key를 이미 쓰고 있어 결제 저장이 unique 위반으로 실패한다.
        Long otherOrderId = jdbcTemplate.queryForObject(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, "
                        + "items_sale_price, coupon_discount, shipping_fee, points_used, payment_amount, "
                        + "recipient, phone, zip_code, address1, expires_at, canceled_at, cancel_reason, "
                        + "created_at, updated_at) "
                        + "values ('20261019-OTHER001', ?, 'CANCELED', 100, 0, 100, 0, 0, 0, 100, '홍길동', "
                        + "'01012345678', '06236', '서울', now(), now(), 'USER', now(), now()) returning id",
                Long.class, memberId);
        jdbcTemplate.update("insert into payment (order_id, payment_key, amount, method, status, approved_at, "
                + "canceled_at, created_at, updated_at) values (?, ?, 100, '카드', 'CANCELED', now(), now(), now(), now())",
                otherOrderId, KEY);

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(gateway.confirms()).hasSize(1);
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "주문 처리 실패"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(cartHas(orderedLine)).isTrue();
        assertStillDeducted(orderNumber);
    }

    @Test
    void 보상_취소가_실패해도_원래_오류를_돌려준다() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmReturns(new PaymentResult(KEY, orderNumber, "WAITING_FOR_DEPOSIT", AMOUNT, "가상계좌", null));
        gateway.cancelThrows(new PaymentTimeoutException("결제사 연결 또는 응답 시간이 초과되었다.", null));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_SUPPORTED"));

        assertThat(gateway.cancels()).hasSize(1);
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
    }

    @Test
    void 승인_타임아웃이면_조회해서_완료면_결제_완료다() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmThrows(new PaymentTimeoutException("결제사 연결 또는 응답 시간이 초과되었다.", null));
        gateway.findReturns(FakePaymentGateway.done(KEY, orderNumber, AMOUNT));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(gateway.finds()).containsExactly(orderNumber);
        assertThat(gateway.cancels()).isEmpty();
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(cartHas(orderedLine)).isFalse();
    }

    @Test
    void 승인_타임아웃이고_결제가_없으면_취소를_시도하고_502이고_대기_그대로다() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmThrows(new PaymentTimeoutException("결제사 연결 또는 응답 시간이 초과되었다.", null));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        assertThat(gateway.finds()).containsExactly(orderNumber);
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "승인 응답 시간 초과"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
        assertThat(paymentCount()).isZero();
        assertStillDeducted(orderNumber);
    }

    @Test
    void 승인과_조회가_모두_실패하고_취소도_실패해도_502다() throws Exception {
        String orderNumber = placeOrder();
        PaymentTimeoutException timeout = new PaymentTimeoutException("결제사 연결 또는 응답 시간이 초과되었다.", null);
        gateway.confirmThrows(timeout);
        gateway.findThrows(timeout);
        gateway.cancelThrows(timeout);

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "승인 응답 시간 초과"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
    }

    @Test
    void 이미_처리된_결제_응답이면_조회해서_결제_완료다() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmThrows(new PaymentGatewayException(400, "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다."));
        gateway.findReturns(FakePaymentGateway.done(KEY, orderNumber, AMOUNT));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(gateway.finds()).containsExactly(orderNumber);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 토스_5xx_응답이면_조회해서_완료면_결제_완료다() throws Exception {
        String orderNumber = placeOrder();
        // 토스가 요청을 받은 뒤 5xx를 줬다면 승인되었을 수 있다.
        gateway.confirmThrows(new PaymentGatewayException(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 오류"));
        gateway.findReturns(FakePaymentGateway.done(KEY, orderNumber, AMOUNT));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(gateway.finds()).containsExactly(orderNumber);
        assertThat(gateway.cancels()).isEmpty();
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 읽지_못한_응답이면_조회해서_완료면_결제_완료다() throws Exception {
        String orderNumber = placeOrder();
        // 2xx 본문을 읽지 못하면 UNKNOWN이다. 승인은 되었을 수 있다.
        gateway.confirmThrows(new PaymentGatewayException(200, "UNKNOWN", "결제사 응답을 읽지 못했다."));
        gateway.findReturns(FakePaymentGateway.done(KEY, orderNumber, AMOUNT));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(gateway.finds()).containsExactly(orderNumber);
    }

    @Test
    void 승인_중_예상하지_못한_예외면_조회하고_결제가_없으면_취소를_시도하고_502다() throws Exception {
        String orderNumber = placeOrder();
        gateway.confirmThrows(new DateTimeParseException("approvedAt을 읽지 못했다", "x", 0));

        confirm(orderNumber, KEY, AMOUNT)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        assertThat(gateway.finds()).containsExactly(orderNumber);
        assertThat(gateway.cancels()).containsExactly(new CancelCall(KEY, "승인 응답 시간 초과"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
        assertThat(paymentCount()).isZero();
    }

    @Test
    void 다른_회원의_주문번호면_404_ORDER_NOT_FOUND() throws Exception {
        String orderNumber = placeOrder();
        Long other = memberService.signup("other@mall.com", "password1", "김철수", "01012345678");

        confirm(bearer(other, Role.USER), orderNumber, KEY, AMOUNT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        confirm("20261020-NOTEXIST", KEY, AMOUNT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(gateway.confirms()).isEmpty();
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING");
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        String orderNumber = placeOrder();

        confirm(orderNumber, "", AMOUNT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        confirm(orderNumber, "k".repeat(201), AMOUNT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        confirm(orderNumber, KEY, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(gateway.confirms()).isEmpty();
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        String orderNumber = placeOrder();

        mockMvc.perform(post("/api/members/me/orders/{orderNumber}/payment", orderNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"" + KEY + "\",\"amount\":" + AMOUNT + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(gateway.confirms()).isEmpty();
    }
}
