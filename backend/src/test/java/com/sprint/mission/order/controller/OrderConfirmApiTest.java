package com.sprint.mission.order.controller;

import static com.sprint.mission.order.controller.OrderQueryApiTest.detailFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.ApiTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.test.web.servlet.ResultActions;

class OrderConfirmApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

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

    Long memberId;
    Long skuId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 100))));
        skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    private String token(Long id) {
        return bearer(id, Role.USER);
    }

    /** 주문을 만든 뒤 SQL로 지정한 금액의 배송 완료 주문으로 바꾼다. */
    private String order(Long ownerId, long paymentAmount, long shippingFee, String status) {
        Long addressId = addressService.create(ownerId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", "101호", true)).id();
        cartService.add(ownerId, new CartAddRequest(skuId, 1));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, ownerId, skuId);
        String orderNumber = orderService.create(ownerId,
                new OrderCreateRequest(List.of(lineId), null, 0, addressId)).orderNumber();
        jdbcTemplate.update("update orders set status = ?, payment_amount = ?, shipping_fee = ?, "
                + "delivered_at = now() where order_number = ?", status, paymentAmount, shippingFee, orderNumber);
        return orderNumber;
    }

    private ResultActions confirm(String token, String orderNumber) throws Exception {
        return mockMvc.perform(post("/api/members/me/orders/{orderNumber}/confirm", orderNumber)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private List<Map<String, Object>> earns() {
        return jdbcTemplate.queryForList("select amount, balance_after, order_id from point_history "
                + "where type = 'EARN'");
    }

    private long balance() {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    @Test
    void 배송_완료_주문을_구매확정하면_등급_적립률로_적립한다() throws Exception {
        // 결제금액 22,500원에서 배송비 2,500원을 뺀 20,000원이 기준이다. BABY 0.5%는 100원이다.
        String orderNumber = order(memberId, 22_500, 2_500, "DELIVERED");

        confirm(token(memberId), orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andDo(document("order/confirm",
                        requestHeaders(AUTHORIZATION),
                        pathParameters(parameterWithName("orderNumber").description("주문번호")),
                        responseFields(detailFields())));

        Long orderId = jdbcTemplate.queryForObject("select id from orders where order_number = ?", Long.class,
                orderNumber);
        assertThat(earns()).hasSize(1);
        assertThat(earns().get(0)).containsEntry("amount", 100L).containsEntry("balance_after", 100L)
                .containsEntry("order_id", orderId);
        assertThat(balance()).isEqualTo(100);
        assertThat(jdbcTemplate.queryForObject("select confirmed_at from orders where id = ?", Object.class,
                orderId)).isNotNull();
    }

    @Test
    void GOLD_회원은_3퍼센트다() throws Exception {
        jdbcTemplate.update("update member set grade = 'GOLD' where id = ?", memberId);
        String orderNumber = order(memberId, 22_500, 2_500, "DELIVERED");

        confirm(token(memberId), orderNumber).andExpect(status().isOk());

        assertThat(balance()).isEqualTo(600);
    }

    @Test
    void 결제금액이_배송비보다_작으면_적립하지_않는다() throws Exception {
        String orderNumber = order(memberId, 100, 2_500, "DELIVERED");

        confirm(token(memberId), orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(earns()).isEmpty();
        assertThat(balance()).isZero();
    }

    @Test
    void 다시_확정하면_200이고_적립은_한_번이다() throws Exception {
        String orderNumber = order(memberId, 22_500, 2_500, "DELIVERED");
        confirm(token(memberId), orderNumber).andExpect(status().isOk());

        confirm(token(memberId), orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(earns()).hasSize(1);
        assertThat(balance()).isEqualTo(100);
    }

    @Test
    void 배송_완료가_아니면_409_ORDER_NOT_CONFIRMABLE() throws Exception {
        for (String state : List.of("PENDING", "PAID", "PREPARING", "SHIPPING")) {
            String orderNumber = order(memberId, 22_500, 2_500, state);

            confirm(token(memberId), orderNumber)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_CONFIRMABLE"));

            assertThat(jdbcTemplate.queryForObject("select status from orders where order_number = ?",
                    String.class, orderNumber)).isEqualTo(state);
        }
        assertThat(earns()).isEmpty();
    }

    @Test
    void 다른_회원의_주문이면_404() throws Exception {
        String orderNumber = order(memberId, 22_500, 2_500, "DELIVERED");
        Long otherId = memberService.signup("other@mall.com", "password1", "김철수", "01099999999");

        confirm(token(otherId), orderNumber)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        confirm(token(memberId), "20261019-NOSUCHXX").andExpect(status().isNotFound());

        assertThat(earns()).isEmpty();
    }

    @Test
    void 토큰_없이_호출하면_401() throws Exception {
        mockMvc.perform(post("/api/members/me/orders/{orderNumber}/confirm", "20261019-NOSUCHXX"))
                .andExpect(status().isUnauthorized());
    }
}
