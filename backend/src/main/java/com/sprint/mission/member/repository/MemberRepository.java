package com.sprint.mission.member.repository;

import com.sprint.mission.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String normalizedEmail);

    /** 같은 회원의 배송지 쓰기를 직렬화하려고 회원 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findWithLockById(@Param("id") Long id);

    boolean existsByEmail(String normalizedEmail);
}
