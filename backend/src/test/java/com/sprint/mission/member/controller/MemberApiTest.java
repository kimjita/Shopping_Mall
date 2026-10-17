package com.sprint.mission.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class MemberApiTest extends ApiTest {

    private static final HeaderDescriptor AUTHORIZATION = headerWithName(HttpHeaders.AUTHORIZATION)
            .description("access token (`Bearer <token>`)");

    @Autowired
    MemberService memberService;

    Long memberId;

    @BeforeEach
    void 회원을_가입시킨다() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private String token() {
        return bearer(memberId, Role.USER);
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "user@mall.com", "password", password))));
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 내_정보를_조회한다() throws Exception {
        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId))
                .andExpect(jsonPath("$.email").value("user@mall.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.skinConcerns").isEmpty())
                .andDo(document("member/me",
                        requestHeaders(AUTHORIZATION),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("phone").type(JsonFieldType.STRING).description("휴대폰 번호 (숫자만)"),
                                fieldWithPath("role").type(JsonFieldType.STRING).description("권한 (`USER`, `ADMIN`)"),
                                fieldWithPath("skinType").type(JsonFieldType.STRING).optional()
                                        .description("피부 타입 (`DRY`, `OILY`, `COMBINATION`, `SENSITIVE`, `NORMAL`). 설정 전에는 `null`"),
                                fieldWithPath("skinTone").type(JsonFieldType.STRING).optional()
                                        .description("피부 톤 (`COOL`, `WARM`, `NEUTRAL`). 설정 전에는 `null`"),
                                fieldWithPath("skinConcerns").type(JsonFieldType.ARRAY)
                                        .description("피부 고민 (선언 순서로 정렬, 없으면 빈 배열)"))));
    }

    @Test
    void 회원가입_직후_내_정보의_피부_프로필은_비어_있다() throws Exception {
        // 가입 때 쓴 영속성 컨텍스트와 다른 요청이라 DB에서 다시 읽는다. 컬럼이 모두 NULL이면 임베디드 값이 null로 로딩된다.
        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value((Object) null))
                .andExpect(jsonPath("$.skinTone").value((Object) null))
                .andExpect(jsonPath("$.skinConcerns").isEmpty());
    }

    @Test
    void 프로필을_수정한다() throws Exception {
        json(patch("/api/members/me"), "{\"name\":\"새이름\",\"phone\":\"010-9999-8888\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.phone").value("01099998888"))
                .andDo(document("member/update-profile",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("이름 (1~30자)"),
                                fieldWithPath("phone").type(JsonFieldType.STRING)
                                        .description("휴대폰 번호 (하이픈 허용, 저장할 때는 숫자만 남김)")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("바뀐 이름"),
                                fieldWithPath("phone").type(JsonFieldType.STRING).description("바뀐 휴대폰 번호 (숫자만)"),
                                fieldWithPath("role").type(JsonFieldType.STRING).description("권한"),
                                fieldWithPath("skinType").type(JsonFieldType.STRING).optional().description("피부 타입"),
                                fieldWithPath("skinTone").type(JsonFieldType.STRING).optional().description("피부 톤"),
                                fieldWithPath("skinConcerns").type(JsonFieldType.ARRAY).description("피부 고민"))));

        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.phone").value("01099998888"));
    }

    @Test
    void 잘못된_이름과_휴대폰_번호는_400() throws Exception {
        json(patch("/api/members/me"), "{\"name\":\"\",\"phone\":\"02-123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 피부_프로필을_저장한다() throws Exception {
        json(put("/api/members/me/skin-profile"),
                "{\"skinType\":\"DRY\",\"skinTone\":\"WARM\",\"skinConcerns\":[\"PORE\",\"WRINKLE\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value("DRY"))
                .andExpect(jsonPath("$.skinTone").value("WARM"))
                .andExpect(jsonPath("$.skinConcerns[0]").value("WRINKLE"))
                .andExpect(jsonPath("$.skinConcerns[1]").value("PORE"))
                .andExpect(jsonPath("$.skinConcerns.length()").value(2))
                .andDo(document("member/update-skin-profile",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("skinType").type(JsonFieldType.STRING).optional()
                                        .description("피부 타입 (`DRY`, `OILY`, `COMBINATION`, `SENSITIVE`, `NORMAL`). 생략하거나 `null`이면 비움"),
                                fieldWithPath("skinTone").type(JsonFieldType.STRING).optional()
                                        .description("피부 톤 (`COOL`, `WARM`, `NEUTRAL`). 생략하거나 `null`이면 비움"),
                                fieldWithPath("skinConcerns").type(JsonFieldType.ARRAY).optional()
                                        .description("피부 고민. 기존 고민을 모두 교체하며, 생략하거나 `null`이면 비움")),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("회원 ID"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("phone").type(JsonFieldType.STRING).description("휴대폰 번호 (숫자만)"),
                                fieldWithPath("role").type(JsonFieldType.STRING).description("권한"),
                                fieldWithPath("skinType").type(JsonFieldType.STRING).optional().description("저장된 피부 타입"),
                                fieldWithPath("skinTone").type(JsonFieldType.STRING).optional().description("저장된 피부 톤"),
                                fieldWithPath("skinConcerns").type(JsonFieldType.ARRAY)
                                        .description("저장된 피부 고민 (선언 순서로 정렬)"))));

        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.skinType").value("DRY"))
                .andExpect(jsonPath("$.skinConcerns.length()").value(2));
    }

    @Test
    void 피부_프로필을_다시_저장하면_고민이_교체되고_빈_값은_비워진다() throws Exception {
        json(put("/api/members/me/skin-profile"),
                "{\"skinType\":\"DRY\",\"skinTone\":\"WARM\",\"skinConcerns\":[\"PORE\",\"WRINKLE\"]}")
                .andExpect(status().isOk());

        json(put("/api/members/me/skin-profile"), "{\"skinConcerns\":[\"ATOPY\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value((Object) null))
                .andExpect(jsonPath("$.skinConcerns.length()").value(1))
                .andExpect(jsonPath("$.skinConcerns[0]").value("ATOPY"));

        json(put("/api/members/me/skin-profile"), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinTone").value((Object) null))
                .andExpect(jsonPath("$.skinConcerns").isEmpty());

        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.skinType").value((Object) null))
                .andExpect(jsonPath("$.skinConcerns").isEmpty());
    }

    @Test
    void 없는_피부타입_값은_400() throws Exception {
        json(put("/api/members/me/skin-profile"), "{\"skinType\":\"OILYY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 피부_고민에_null이_있으면_400() throws Exception {
        json(put("/api/members/me/skin-profile"), "{\"skinType\":\"DRY\",\"skinConcerns\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 비밀번호를_바꾸면_기존_refresh_token이_폐기된다() throws Exception {
        MvcResult loginResult = login("password1").andExpect(status().isOk()).andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        json(patch("/api/members/me/password"), "{\"currentPassword\":\"password1\",\"newPassword\":\"newPassword2\"}")
                .andExpect(status().isNoContent())
                .andDo(document("member/change-password",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("currentPassword").type(JsonFieldType.STRING).description("현재 비밀번호"),
                                fieldWithPath("newPassword").type(JsonFieldType.STRING)
                                        .description("새 비밀번호 (가입과 같은 규칙: 8~64자, 영문과 숫자를 각각 1자 이상 포함)"))));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        login("password1").andExpect(status().isUnauthorized());
        login("newPassword2").andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호가_틀리면_400_PASSWORD_MISMATCH() throws Exception {
        json(patch("/api/members/me/password"), "{\"currentPassword\":\"wrong-pass1\",\"newPassword\":\"newPassword2\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        login("password1").andExpect(status().isOk());
    }

    @Test
    void 새_비밀번호가_규칙에_맞지_않으면_400() throws Exception {
        json(patch("/api/members/me/password"), "{\"currentPassword\":\"password1\",\"newPassword\":\"abcdefgh\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    void 탈퇴하면_로그인할_수_없다() throws Exception {
        MvcResult loginResult = login("password1").andExpect(status().isOk()).andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        json(delete("/api/members/me"), "{\"password\":\"password1\"}")
                .andExpect(status().isNoContent())
                .andDo(document("member/withdraw",
                        requestHeaders(AUTHORIZATION),
                        requestFields(
                                fieldWithPath("password").type(JsonFieldType.STRING).description("현재 비밀번호"))));

        login("password1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴할_때_비밀번호가_틀리면_400_PASSWORD_MISMATCH() throws Exception {
        json(delete("/api/members/me"), "{\"password\":\"wrong-pass1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        login("password1").andExpect(status().isOk());
    }

    @Test
    void 탈퇴_후_남은_access_token으로_내_정보를_요청하면_404() throws Exception {
        String token = token();
        json(delete("/api/members/me"), "{\"password\":\"password1\"}").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 탈퇴한_이메일로는_다시_가입할_수_없다() throws Exception {
        json(delete("/api/members/me"), "{\"password\":\"password1\"}").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "user@mall.com",
                                "password", "password1", "name", "홍길동", "phone", "01012345678"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 토큰_없이_내_정보를_요청하면_401() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
