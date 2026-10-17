package com.sprint.mission.auth.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * refresh token 한 개다. 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * 같은 로그인에서 회전(rotation)으로 이어지는 토큰들은 같은 familyId를 가진다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private UUID familyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private Instant revokedAt;

    public static RefreshToken issue(Long memberId, UUID familyId, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.memberId = memberId;
        token.familyId = familyId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
