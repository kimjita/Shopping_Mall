package com.sprint.mission.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookies {

    private static final String PATH = "/api/auth";

    private final RefreshTokenProperties properties;

    public RefreshTokenCookies(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return build(token, properties.ttl());
    }

    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
