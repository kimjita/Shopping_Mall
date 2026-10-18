package com.sprint.mission.member.repository;

import com.sprint.mission.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String normalizedEmail);

    /** 같은 회원의 배송지 쓰기를 직렬화하려고 회원 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findWithLockById(@Param("id") Long id);

    /**
     * 적립금 잔액을 delta만큼 바꾸는 한 문장이다. 결과가 음수가 되면 바꾸지 않고 0을 돌려준다.
     * 읽기 전용 매핑 컬럼이라 JPQL로는 갱신할 수 없어 네이티브 쿼리를 쓴다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update member set point_balance = point_balance + :delta, updated_at = :now "
            + "where id = :id and point_balance + :delta >= 0", nativeQuery = true)
    int changePointBalance(@Param("id") Long id, @Param("delta") long delta, @Param("now") Instant now);

    /** 영속성 컨텍스트를 거치지 않고 DB의 현재 잔액을 읽는다. */
    @Query(value = "select point_balance from member where id = :id", nativeQuery = true)
    long findPointBalanceById(@Param("id") Long id);

    boolean existsByEmail(String normalizedEmail);
}
