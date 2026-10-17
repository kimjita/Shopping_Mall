package com.sprint.mission.member.repository;

import com.sprint.mission.member.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByIdAndMemberId(Long id, Long memberId);

    /** 기본 배송지 먼저, 그다음 먼저 만든 순서다. createdAt이 같으면 id 순서다. */
    @Query("select a from Address a where a.memberId = :memberId "
            + "order by a.isDefault desc, a.createdAt asc, a.id asc")
    List<Address> findAllByMemberId(Long memberId);
}
