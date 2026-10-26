package com.sprint.mission.admin;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.support.ApiTest;
import com.sprint.mission.support.MutableClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class AdminDashboardApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    /** 서울 2026-10-26 10:00이다. */
    private static final Instant NOW = Instant.parse("2026-10-26T01:00:00Z");
    /** 서울 오늘 00:00과 내일 00:00이다. */
    private static final Instant TODAY_START = Instant.parse("2026-10-25T15:00:00Z");
    private static final Instant TOMORROW_START = Instant.parse("2026-10-26T15:00:00Z");

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
    JdbcTemplate jdbcTemplate;

    private final AtomicInteger sequence = new AtomicInteger();
    private Long memberId;
    private Long brandId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        brandId = jdbcTemplate.queryForObject(
                "insert into brand (name, created_at, updated_at) values ('브랜드', now(), now()) returning id",
                Long.class);
        categoryId = jdbcTemplate.queryForObject("select id from category limit 1", Long.class);
        memberId = member("owner@test.com", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private ResultActions dashboard(String token) throws Exception {
        return mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions dashboard() throws Exception {
        return dashboard(bearer(1L, Role.ADMIN));
    }

    private Long member(String email, String status, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, withdrawn_at, created_at, "
                        + "updated_at) values (?, 'pw', '회원', '01000000000', 'USER', ?, ?, ?, ?) returning id",
                Long.class, email, status, "WITHDRAWN".equals(status) ? Timestamp.from(createdAt) : null,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    /** 주문 하나를 넣는다. approvedAt이 null이면 결제 기록은 넣지 않는다. */
    private void order(String status, long paymentAmount, Instant approvedAt) {
        int n = sequence.incrementAndGet();
        Long orderId = jdbcTemplate.queryForObject(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, items_sale_price, "
                        + "coupon_discount, shipping_fee, points_used, payment_amount, recipient, phone, zip_code, "
                        + "address1, expires_at, created_at, updated_at) "
                        + "values (?, ?, ?, 0, 0, 0, 0, 0, 0, ?, '회원', '01012345678', '06236', '서울', "
                        + "now(), now(), now()) returning id", Long.class, "ORD-" + n, memberId, status, paymentAmount);
        if (approvedAt != null) {
            jdbcTemplate.update("insert into payment (order_id, payment_key, amount, method, status, approved_at, "
                    + "created_at, updated_at) values (?, ?, ?, 'CARD', 'DONE', ?, now(), now())",
                    orderId, "pk-" + n, paymentAmount, Timestamp.from(approvedAt));
        }
    }

    private void sku(String productStatus, int stock) {
        int n = sequence.incrementAndGet();
        Long productId = jdbcTemplate.queryForObject(
                "insert into product (brand_id, category_id, name, description, base_price, status, min_price, "
                        + "search_text, created_at, updated_at) values (?, ?, ?, '설명', 10000, ?, 10000, ?, "
                        + "now(), now()) returning id", Long.class, brandId, categoryId, "상품" + n, productStatus,
                "상품" + n);
        jdbcTemplate.update("insert into sku (product_id, name, stock, created_at, updated_at) "
                + "values (?, '옵션', ?, now(), now())", productId, stock);
    }

    @Test
    void 오늘_지표를_서울_기준으로_센다() throws Exception {
        // 어제 23:59(서울)와 내일 00:00(서울) 승인은 제외하고, 오늘 00:00 승인과 오늘 23:59 승인은 포함한다.
        order("PAID", 1_000, TODAY_START.minusSeconds(60));
        order("PAID", 2_000, TODAY_START);
        order("DELIVERED", 3_000, TOMORROW_START.minusSeconds(60));
        order("CONFIRMED", 4_000, NOW);
        order("PAID", 5_000, TOMORROW_START);
        // 오늘 승인됐지만 취소된 주문과 결제 대기 주문은 제외한다.
        order("CANCELED", 7_000, NOW);
        order("PENDING", 8_000, null);

        member("yesterday@test.com", "ACTIVE", TODAY_START.minusSeconds(1));
        member("today0@test.com", "ACTIVE", TODAY_START);
        member("today1@test.com", "WITHDRAWN", NOW);
        member("tomorrow@test.com", "ACTIVE", TOMORROW_START);

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-26"))
                .andExpect(jsonPath("$.todayPaidCount").value(3))
                .andExpect(jsonPath("$.todayPaidAmount").value(9_000))
                .andExpect(jsonPath("$.todaySignupCount").value(2))
                .andDo(document("admin-dashboard/get",
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("date").type(JsonFieldType.STRING)
                                        .description("집계 기준일(서울, `yyyy-MM-dd`)"),
                                fieldWithPath("todayPaidCount").type(JsonFieldType.NUMBER)
                                        .description("오늘 결제가 승인된 주문 수(취소 제외)"),
                                fieldWithPath("todayPaidAmount").type(JsonFieldType.NUMBER)
                                        .description("오늘 결제가 승인된 주문의 결제 금액 합(원, 취소 제외)"),
                                fieldWithPath("paidCount").type(JsonFieldType.NUMBER)
                                        .description("지금 결제 완료(`PAID`) 상태인 주문 수"),
                                fieldWithPath("preparingCount").type(JsonFieldType.NUMBER)
                                        .description("지금 상품 준비 중(`PREPARING`) 상태인 주문 수"),
                                fieldWithPath("shippingCount").type(JsonFieldType.NUMBER)
                                        .description("지금 배송 중(`SHIPPING`) 상태인 주문 수"),
                                fieldWithPath("lowStockSkuCount").type(JsonFieldType.NUMBER)
                                        .description("재고가 5개 이하인 판매 중 상품의 옵션 수"),
                                fieldWithPath("todaySignupCount").type(JsonFieldType.NUMBER)
                                        .description("오늘 가입한 회원 수(탈퇴 회원 포함)"))));
    }

    @Test
    void 지표가_없으면_모두_0이다() throws Exception {
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayPaidCount").value(0))
                .andExpect(jsonPath("$.todayPaidAmount").value(0))
                .andExpect(jsonPath("$.paidCount").value(0))
                .andExpect(jsonPath("$.preparingCount").value(0))
                .andExpect(jsonPath("$.shippingCount").value(0))
                .andExpect(jsonPath("$.lowStockSkuCount").value(0))
                .andExpect(jsonPath("$.todaySignupCount").value(0));
    }

    @Test
    void 상태별_처리_대기_수를_센다() throws Exception {
        order("PAID", 1_000, null);
        order("PAID", 1_000, null);
        order("PREPARING", 1_000, null);
        order("SHIPPING", 1_000, null);
        order("SHIPPING", 1_000, null);
        order("SHIPPING", 1_000, null);
        order("DELIVERED", 1_000, null);
        order("CANCELED", 1_000, null);

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidCount").value(2))
                .andExpect(jsonPath("$.preparingCount").value(1))
                .andExpect(jsonPath("$.shippingCount").value(3));
    }

    @Test
    void 재고_부족_옵션은_판매_중_상품만_센다() throws Exception {
        sku("ON_SALE", 5);
        sku("ON_SALE", 0);
        sku("ON_SALE", 6);
        sku("STOPPED", 1);
        sku("DELETED", 0);

        dashboard().andExpect(status().isOk()).andExpect(jsonPath("$.lowStockSkuCount").value(2));
    }

    @Test
    void 일반_회원이면_403() throws Exception {
        dashboard(bearer(2L, Role.USER)).andExpect(status().isForbidden());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }
}
