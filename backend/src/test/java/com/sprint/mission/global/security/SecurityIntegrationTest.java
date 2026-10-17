package com.sprint.mission.global.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprint.mission.member.domain.Role;
import com.sprint.mission.support.ApiTest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class SecurityIntegrationTest extends ApiTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private Clock clock;

    @Test
    void 토큰_없이_API를_호출하면_401_공통_포맷() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", is("로그인이 필요합니다.")));
    }

    @Test
    void 위조된_토큰은_401() throws Exception {
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void 만료된_토큰은_401() throws Exception {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("7")
                .claim("role", Role.USER.name())
                .issuedAt(now.minus(10, ChronoUnit.MINUTES))
                .expiresAt(now.minus(1, ChronoUnit.MINUTES))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void 유효한_토큰이면_회원_id를_꺼낸다() throws Exception {
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, bearer(7L, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void USER가_관리자_API를_호출하면_403() throws Exception {
        mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, bearer(7L, Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.message", is("접근 권한이 없습니다.")));
    }

    @Test
    void ADMIN은_관리자_API에_접근한다() throws Exception {
        mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, bearer(1L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void 허용된_출처의_preflight는_통과한다() throws Exception {
        mockMvc.perform(options("/api/test/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }
}
