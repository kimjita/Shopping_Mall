package com.sprint.mission.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.auth.service.RefreshTokenGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void 만료_시각이_지나면_만료다() {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        RefreshToken token = RefreshToken.issue(1L, UUID.randomUUID(), "a".repeat(64), now);

        assertThat(token.isExpired(now)).isTrue();
        assertThat(token.isExpired(now.minusSeconds(1))).isFalse();
    }

    @Test
    void 새_토큰은_사용되거나_폐기되지_않았다() {
        RefreshToken token = RefreshToken.issue(1L, UUID.randomUUID(), "a".repeat(64), Instant.now());

        assertThat(token.isUsed()).isFalse();
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void 해시는_64자_hex이고_결정적이다() {
        String hash = RefreshTokenGenerator.hash("some-token");

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(RefreshTokenGenerator.hash("some-token")).isEqualTo(hash);
        assertThat(RefreshTokenGenerator.hash("other-token")).isNotEqualTo(hash);
    }

    @Test
    void 생성한_토큰은_패딩_없는_base64url_43자이고_매번_다르다() {
        RefreshTokenGenerator generator = new RefreshTokenGenerator();

        String first = generator.generate();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(generator.generate()).isNotEqualTo(first);
    }
}
