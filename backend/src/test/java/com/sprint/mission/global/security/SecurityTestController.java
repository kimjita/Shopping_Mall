package com.sprint.mission.global.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityTestController {

    @GetMapping("/api/test/me")
    Long me(@CurrentMemberId Long memberId) {
        return memberId;
    }

    @GetMapping("/api/admin/test")
    String admin() {
        return "ok";
    }
}
