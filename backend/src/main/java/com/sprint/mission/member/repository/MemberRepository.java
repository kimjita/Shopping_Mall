package com.sprint.mission.member.repository;

import com.sprint.mission.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);
}
