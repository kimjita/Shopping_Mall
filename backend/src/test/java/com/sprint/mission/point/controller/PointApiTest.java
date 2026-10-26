package com.sprint.mission.point.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.OrderFixtures;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

class PointApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    private static final HeaderDescriptor ADMIN_AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("관리자 access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    @Autowired
    PointService pointService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        OrderFixtures.insertCanceledOrders(jdbcTemplate, memberId, 8);
    }

    private ResultActions grant(String token, Long targetId, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/members/{memberId}/points", targetId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions grantAsAdmin(String body) throws Exception {
        return grant(bearer(1L, Role.ADMIN), memberId, body);
    }

    @Test
    void 내_적립금과_내역을_본다() throws Exception {
        pointService.earn(memberId, 2_000, "구매 적립", 7L);
        pointService.use(memberId, 500, "주문 사용", 8L);

        mockMvc.perform(get("/api/members/me/points")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1_500))
                .andDo(document("point/balance",
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("balance").type(JsonFieldType.NUMBER).description("현재 적립금 잔액 (원)"))));

        mockMvc.perform(get("/api/members/me/points/history").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("USE"))
                .andExpect(jsonPath("$.items[0].amount").value(500))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(1_500))
                .andExpect(jsonPath("$.items[0].reason").value("주문 사용"))
                .andExpect(jsonPath("$.items[0].orderId").value(8))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andDo(document("point/history",
                        requestHeaders(AUTHORIZATION),
                        queryParameters(
                                parameterWithName("cursor").optional().description("이전 응답의 `nextCursor`"),
                                parameterWithName("size").optional().description("한 페이지 크기 (1~50, 기본 20)")),
                        responseFields(
                                fieldWithPath("items").type(JsonFieldType.ARRAY).description("적립금 내역. 최근 순"),
                                fieldWithPath("items[].id").type(JsonFieldType.NUMBER).description("내역 ID"),
                                fieldWithPath("items[].type").type(JsonFieldType.STRING)
                                        .description("종류 (`EARN` 적립, `USE` 사용, `RESTORE` 복원, `ADMIN_GRANT` 관리자 지급)"),
                                fieldWithPath("items[].amount").type(JsonFieldType.NUMBER)
                                        .description("변동 금액 (원, 항상 양수. 증감은 `type`으로 구분한다)"),
                                fieldWithPath("items[].balanceAfter").type(JsonFieldType.NUMBER)
                                        .description("변동 직후 잔액 (원)"),
                                fieldWithPath("items[].reason").type(JsonFieldType.STRING).description("사유"),
                                fieldWithPath("items[].orderId").type(JsonFieldType.NUMBER).optional()
                                        .description("관련 주문 ID. 주문과 무관하면 `null`"),
                                fieldWithPath("items[].createdAt").type(JsonFieldType.STRING)
                                        .description("기록 시각 (ISO-8601)"),
                                fieldWithPath("nextCursor").type(JsonFieldType.STRING).optional()
                                        .description("다음 페이지 커서. 마지막 페이지면 `null`"))));

        mockMvc.perform(get("/api/members/me/points/history").param("size", "20")
                        .param("cursor", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 잘못된_커서와_size는_400이다() throws Exception {
        String token = bearer(memberId, Role.USER);
        mockMvc.perform(get("/api/members/me/points/history").param("cursor", "abc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/members/me/points/history").param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/members/me/points/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자가_적립금을_지급한다() throws Exception {
        grantAsAdmin("""
                {"amount":5000,"reason":"이벤트 보상"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5_000))
                .andDo(document("admin-point/grant",
                        pathParameters(parameterWithName("memberId").description("지급 대상 회원 ID")),
                        requestHeaders(ADMIN_AUTHORIZATION),
                        requestFields(
                                fieldWithPath("amount").type(JsonFieldType.NUMBER)
                                        .description("지급 금액 (원, 1~1,000,000)"),
                                fieldWithPath("reason").type(JsonFieldType.STRING)
                                        .description("지급 사유 (1~100자)")),
                        responseFields(
                                fieldWithPath("balance").type(JsonFieldType.NUMBER)
                                        .description("지급 후 적립금 잔액 (원)"))));

        assertThat(pointService.balance(memberId)).isEqualTo(5_000);
        assertThat(jdbcTemplate.queryForObject(
                "select type from point_history where member_id = ?", String.class, memberId))
                .isEqualTo("ADMIN_GRANT");
    }

    @Test
    void USER는_지급할_수_없다() throws Exception {
        grant(bearer(memberId, Role.USER), memberId, """
                {"amount":5000,"reason":"이벤트 보상"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(pointService.balance(memberId)).isZero();
    }

    @Test
    void 지급_금액이_0이거나_1000001이면_400() throws Exception {
        for (long amount : new long[] {0, -1, 1_000_001}) {
            grantAsAdmin("{\"amount\":" + amount + ",\"reason\":\"이벤트 보상\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        grantAsAdmin("{\"amount\":1000000,\"reason\":\"최대\"}").andExpect(status().isOk());
    }

    @Test
    void 사유가_비었거나_100자를_넘으면_400() throws Exception {
        grantAsAdmin("{\"amount\":100,\"reason\":\"  \"}").andExpect(status().isBadRequest());
        grantAsAdmin("{\"amount\":100,\"reason\":\"\u3000\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        grantAsAdmin("{\"amount\":100}").andExpect(status().isBadRequest());
        grantAsAdmin("{\"amount\":100,\"reason\":\"" + "가".repeat(101) + "\"}").andExpect(status().isBadRequest());
        grantAsAdmin("{\"amount\":100,\"reason\":\"" + "가".repeat(100) + "\"}").andExpect(status().isOk());
    }

    @Test
    void 지급_사유에_제어_문자가_있으면_400() throws Exception {
        for (String reason : new String[] {"사유\\u001C", "사유\\u001F", "사유\\u0000", "사유\\u007F", "사유\\n"}) {
            grantAsAdmin("{\"amount\":100,\"reason\":\"" + reason + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 금액에_소수를_보내면_400() throws Exception {
        grantAsAdmin("{\"amount\":1.9,\"reason\":\"이벤트 보상\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_회원에게는_지급할_수_없다() throws Exception {
        grant(bearer(1L, Role.ADMIN), 999_999L, """
                {"amount":100,"reason":"이벤트 보상"}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }
}
