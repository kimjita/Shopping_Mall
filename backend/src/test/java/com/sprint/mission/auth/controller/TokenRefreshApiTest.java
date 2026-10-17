package com.sprint.mission.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.auth.service.RefreshTokenGenerator;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class TokenRefreshApiTest extends ApiTest {

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    Clock clock;

    @BeforeEach
    void 회원을_가입시킨다() {
        memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@mall.com", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn();
        return cookieValue(result);
    }

    private String cookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", token)));
    }

    @Test
    void refresh_쿠키로_새_토큰을_받는다() throws Exception {
        String before = login();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", before)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.accessToken").isString())
                .andDo(document("auth/refresh",
                        requestCookies(
                                cookieWithName("refresh_token").description("로그인 또는 이전 재발급에서 받은 refresh token")),
                        responseHeaders(
                                headerWithName(HttpHeaders.SET_COOKIE)
                                        .description("회전된 새 refresh token 쿠키. 이전 토큰은 다시 쓸 수 없어요. "
                                                + "실패(401)하면 쿠키를 지우는 `Max-Age=0` 쿠키가 내려와요.")),
                        responseFields(
                                fieldWithPath("accessToken").type(JsonFieldType.STRING)
                                        .description("새 access token (JWT)"),
                                fieldWithPath("tokenType").type(JsonFieldType.STRING).description("항상 `Bearer`"),
                                fieldWithPath("expiresIn").type(JsonFieldType.NUMBER)
                                        .description("access token 유효 시간(초)"))))
                .andReturn();

        String after = cookieValue(result);
        assertThat(after).isNotEqualTo(before);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly").contains("Path=/api/auth").contains("Max-Age=1209600");
        Integer families = jdbcTemplate.queryForObject(
                "select count(distinct family_id) from refresh_token", Integer.class);
        assertThat(families).isEqualTo(1);
    }

    @Test
    void 회전된_이전_토큰을_다시_쓰면_401이고_패밀리가_폐기된다() throws Exception {
        String a = login();
        String b = cookieValue(refresh(a).andExpect(status().isOk()).andReturn());

        MvcResult reuse = refresh(a)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REFRESH_TOKEN.message()))
                .andReturn();
        assertThat(reuse.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        refresh(b).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        Integer alive = jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where revoked_at is null", Integer.class);
        assertThat(alive).isZero();
    }

    @Test
    void 다른_기기_로그인은_재사용_감지에_영향이_없다() throws Exception {
        String x = login();
        String y = login();
        refresh(x).andExpect(status().isOk());

        refresh(x).andExpect(status().isUnauthorized());

        refresh(y).andExpect(status().isOk());
    }

    @Test
    void 쿠키가_없으면_401() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void 모르는_토큰은_401() throws Exception {
        refresh("unknown-token").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 만료된_refresh_token은_401() throws Exception {
        String token = login();
        jdbcTemplate.update("update refresh_token set expires_at = now() - interval '1 second' where token_hash = ?",
                RefreshTokenGenerator.hash(token));

        refresh(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 탈퇴한_회원은_401() throws Exception {
        String token = login();
        jdbcTemplate.update("update member set status = 'WITHDRAWN'");

        refresh(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 로그아웃하면_refresh가_막히고_쿠키가_만료된다() throws Exception {
        String token = login();

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isNoContent())
                .andDo(document("auth/logout",
                        requestCookies(
                                cookieWithName("refresh_token").description("폐기할 refresh token. 없어도 돼요."))))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=").contains("Max-Age=0");
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void 쿠키_없이_로그아웃해도_204() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void 모르는_토큰으로_로그아웃해도_204() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", "unknown-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void 만료된_access_token_헤더를_붙여도_refresh는_동작한다() throws Exception {
        String token = login();
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("7")
                .claim("role", Role.USER.name())
                .issuedAt(now.minus(10, ChronoUnit.MINUTES))
                .expiresAt(now.minus(1, ChronoUnit.MINUTES))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                        .cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }
}
