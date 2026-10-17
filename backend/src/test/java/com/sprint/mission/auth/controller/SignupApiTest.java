package com.sprint.mission.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.domain.MemberStatus;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.support.ApiTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class SignupApiTest extends ApiTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private ResultActions signup(String email, String password, String name, String phone) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", email, "password", password, "name", name, "phone", phone));
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 회원가입에_성공한다() throws Exception {
        signup("user@mall.com", "password1", "홍길동", "010-1234-5678")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/members/me"))
                .andExpect(jsonPath("$.memberId").value(instanceOf(Number.class)))
                .andDo(document("auth/signup",
                        requestFields(
                                fieldWithPath("email").type(JsonFieldType.STRING)
                                        .description("이메일 (앞뒤 공백 제거·소문자로 정규화, 255자 이하)"),
                                fieldWithPath("password").type(JsonFieldType.STRING)
                                        .description("비밀번호 (8~64자, 영문과 숫자를 각각 1자 이상 포함)"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("이름 (1~30자)"),
                                fieldWithPath("phone").type(JsonFieldType.STRING)
                                        .description("휴대폰 번호 (하이픈 허용, 저장할 때는 숫자만 남김)")),
                        responseFields(
                                fieldWithPath("memberId").type(JsonFieldType.NUMBER).description("가입한 회원 ID"))));

        Member saved = memberRepository.findByEmail("user@mall.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void 비밀번호는_해시로_저장된다() throws Exception {
        signup("user@mall.com", "password1", "홍길동", "01012345678").andExpect(status().isCreated());

        String stored = memberRepository.findByEmail("user@mall.com").orElseThrow().getPassword();
        assertThat(stored).isNotEqualTo("password1");
        assertThat(passwordEncoder.matches("password1", stored)).isTrue();
    }

    @Test
    void 이메일은_정규화되어_저장된다() throws Exception {
        signup(" A@Mall.com ", "password1", "홍길동", "01012345678").andExpect(status().isCreated());

        assertThat(memberRepository.findByEmail("a@mall.com")).isPresent();
    }

    @Test
    void 대소문자만_다른_이메일은_중복이다() throws Exception {
        signup("a@mall.com", "password1", "홍길동", "01012345678").andExpect(status().isCreated());

        signup(" A@Mall.COM ", "password1", "김철수", "01099998888")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 휴대폰_번호는_숫자만_저장된다() throws Exception {
        signup("user@mall.com", "password1", "홍길동", "010-1234-5678").andExpect(status().isCreated());

        assertThat(memberRepository.findByEmail("user@mall.com").orElseThrow().getPhone())
                .isEqualTo("01012345678");
    }

    @Test
    void 규칙에_맞지_않는_입력은_필드_오류() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\"abcdefgh\",\"name\":\"\",\"phone\":\"02-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("email", "password", "name", "phone")));
    }

    @Test
    void 한글이_섞인_비밀번호는_400() throws Exception {
        signup("user@mall.com", "비밀번호abc12345", "홍길동", "01012345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("password")));
    }

    @Test
    void 긴_한글_비밀번호도_500이_아니라_400() throws Exception {
        signup("user@mall.com", "가".repeat(30) + "a1", "홍길동", "01012345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@mall.com\",\"password\":\"password1\","
                                + "\"name\":\"홍길동\",\"phone\":\"01012345678\"}"))
                .andExpect(status().isCreated());
    }
}
