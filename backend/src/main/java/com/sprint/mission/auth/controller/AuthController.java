package com.sprint.mission.auth.controller;

import com.sprint.mission.auth.RefreshTokenCookies;
import com.sprint.mission.auth.dto.IssuedTokens;
import com.sprint.mission.auth.dto.LoginRequest;
import com.sprint.mission.auth.dto.SignupRequest;
import com.sprint.mission.auth.dto.SignupResponse;
import com.sprint.mission.auth.dto.TokenResponse;
import com.sprint.mission.auth.service.AuthService;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.global.error.ErrorResponse;
import com.sprint.mission.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return invalidRefreshToken();
        }
        try {
            IssuedTokens tokens = authService.refresh(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(tokens.refreshToken()).toString())
                    .body(TokenResponse.bearer(tokens.accessToken(), tokens.expiresIn()));
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.INVALID_REFRESH_TOKEN) {
                throw e;
            }
            return invalidRefreshToken();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.expire().toString())
                .build();
    }

    /** 실패한 응답에도 쿠키를 지우는 Set-Cookie를 같이 내려요. */
    private ResponseEntity<ErrorResponse> invalidRefreshToken() {
        return ResponseEntity.status(ErrorCode.INVALID_REFRESH_TOKEN.status())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.expire().toString())
                .body(ErrorResponse.of(ErrorCode.INVALID_REFRESH_TOKEN));
    }
}
