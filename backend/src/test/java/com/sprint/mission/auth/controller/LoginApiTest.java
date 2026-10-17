package com.sprint.mission.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprint.mission.auth.service.RefreshTokenGenerator;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class LoginApiTest extends ApiTest {

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;

    @BeforeEach
    void 회원을_가입시킨다() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String refreshCookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    @Test
    void 로그인하면_access_token과_refresh_쿠키를_준다() throws Exception {
        MvcResult result = login("user@mall.com", "password1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.accessToken").isString())
                .andDo(document("auth/login",
                        requestFields(
                                fieldWithPath("email").type(JsonFieldType.STRING)
                                        .description("이메일 (앞뒤 공백 제거·소문자로 정규화해서 찾음)"),
                                fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호")),
                        responseHeaders(
                                headerWithName(HttpHeaders.SET_COOKIE)
                                        .description("refresh token 쿠키 (`refresh_token`, HttpOnly, "
                                                + "`Path=/api/auth`, 14일). 본문에는 담지 않아요.")),
                        responseFields(
                                fieldWithPath("accessToken").type(JsonFieldType.STRING)
                                        .description("access token (JWT)"),
                                fieldWithPath("tokenType").type(JsonFieldType.STRING)
                                        .description("항상 `Bearer`"),
                                fieldWithPath("expiresIn").type(JsonFieldType.NUMBER)
                                        .description("access token 유효 시간(초)"))))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("refresh_token=")
                .contains("HttpOnly")
                .contains("Path=/api/auth")
                .contains("Max-Age=1209600")
                .contains("SameSite=Lax");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refreshCookieValue(result));
    }

    @Test
    void 이메일은_정규화해서_찾는다() throws Exception {
        login("  USER@Mall.com ", "password1").andExpect(status().isOk());
    }

    @Test
    void 발급된_access_token으로_인증된다() throws Exception {
        MvcResult result = login("user@mall.com", "password1").andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        mockMvc.perform(get("/api/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(memberId)));
    }

    @Test
    void DB에는_refresh_token_해시만_저장된다() throws Exception {
        MvcResult result = login("user@mall.com", "password1").andExpect(status().isOk()).andReturn();
        String cookieValue = refreshCookieValue(result);

        List<String> hashes = jdbcTemplate.queryForList("select token_hash from refresh_token", String.class);

        assertThat(hashes).containsExactly(RefreshTokenGenerator.hash(cookieValue));
        assertThat(hashes.get(0)).isNotEqualTo(cookieValue);
    }

    @Test
    void 로그인할_때마다_새_family로_저장된다() throws Exception {
        login("user@mall.com", "password1").andExpect(status().isOk());
        login("user@mall.com", "password1").andExpect(status().isOk());

        Integer families = jdbcTemplate.queryForObject(
                "select count(distinct family_id) from refresh_token where member_id = ?", Integer.class, memberId);
        assertThat(families).isEqualTo(2);
    }

    @Test
    void 비밀번호가_틀리면_401() throws Exception {
        login("user@mall.com", "wrong-password1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.message()));

        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token", Integer.class)).isZero();
    }

    @Test
    void 없는_이메일도_같은_401() throws Exception {
        login("nobody@mall.com", "password1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.message()));
    }

    @Test
    void 탈퇴한_회원도_같은_401() throws Exception {
        jdbcTemplate.update("update member set status = 'WITHDRAWN' where id = ?", memberId);

        login("user@mall.com", "password1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.message()));
    }

    @Test
    void 이메일이나_비밀번호가_비어_있으면_400() throws Exception {
        login("", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
