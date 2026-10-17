package com.sprint.mission.auth.repository;

import com.sprint.mission.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 아직 쓰지 않았고 폐기되지 않은 토큰만 사용 처리한다. 동시에 호출하면 행 잠금 때문에 하나만 1을 받는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.usedAt = :now, t.updatedAt = :now
            where t.id = :id and t.usedAt is null and t.revokedAt is null
            """)
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
            where t.memberId = :memberId and t.revokedAt is null
            """)
    int revokeAllByMemberId(@Param("memberId") Long memberId, @Param("now") Instant now);
}
