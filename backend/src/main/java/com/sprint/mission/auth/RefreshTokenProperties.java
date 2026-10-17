package com.sprint.mission.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth.refresh")
public record RefreshTokenProperties(Duration ttl, String cookieName, boolean cookieSecure, String cookieSameSite) {
}
