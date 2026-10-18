package com.sprint.mission.point.repository;

import com.sprint.mission.point.domain.PointHistory;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findByMemberIdOrderByIdDesc(Long memberId, Limit limit);

    List<PointHistory> findByMemberIdAndIdLessThanOrderByIdDesc(Long memberId, Long id, Limit limit);
}
