package com.sprint.mission.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = "app.auth.refresh.cookie-name=custom_rt")
class RefreshCookieNameTest extends ApiTest {

    @Autowired
    MemberService memberService;

    @Test
    void 설정한_쿠키_이름으로_재발급된다() throws Exception {
        memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@mall.com", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("custom_rt");
        assertThat(cookie).isNotNull();

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("custom_rt", cookie.getValue())))
                .andExpect(status().isOk());
    }
}
