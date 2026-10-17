package com.sprint.mission.auth.controller;

import com.sprint.mission.auth.RefreshTokenCookies;
import com.sprint.mission.auth.dto.IssuedTokens;
import com.sprint.mission.auth.dto.LoginRequest;
import com.sprint.mission.auth.dto.SignupRequest;
import com.sprint.mission.auth.dto.SignupResponse;
import com.sprint.mission.auth.dto.TokenResponse;
import com.sprint.mission.auth.service.AuthService;
import com.sprint.mission.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Long memberId = memberService.signup(request.email(), request.password(), request.name(), request.phone());
        return ResponseEntity.created(URI.create("/api/members/me")).body(new SignupResponse(memberId));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        IssuedTokens tokens = authService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(tokens.refreshToken()).toString())
                .body(TokenResponse.bearer(tokens.accessToken(), tokens.expiresIn()));
    }
}
