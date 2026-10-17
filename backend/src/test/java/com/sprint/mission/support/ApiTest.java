package com.sprint.mission.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.global.security.JwtTokenProvider;
import com.sprint.mission.member.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureRestDocs
public abstract class ApiTest extends IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    protected String bearer(Long memberId, Role role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, role);
    }
}
