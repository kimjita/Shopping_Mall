package com.sprint.mission.auth.controller;

import com.sprint.mission.auth.dto.SignupRequest;
import com.sprint.mission.auth.dto.SignupResponse;
import com.sprint.mission.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Long memberId = memberService.signup(request.email(), request.password(), request.name(), request.phone());
        return ResponseEntity.created(URI.create("/api/members/me")).body(new SignupResponse(memberId));
    }
}
