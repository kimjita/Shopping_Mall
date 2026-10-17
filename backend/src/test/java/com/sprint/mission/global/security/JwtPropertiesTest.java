package com.sprint.mission.global.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void 짧은_secret은_거부한다() {
        assertThatThrownBy(() -> new JwtProperties("short", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT_SECRET은 32바이트 이상이어야 합니다.");
    }

    @Test
    void null_secret은_거부한다() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
