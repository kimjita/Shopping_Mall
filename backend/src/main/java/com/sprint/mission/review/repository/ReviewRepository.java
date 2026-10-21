package com.sprint.mission.review.repository;

import com.sprint.mission.review.domain.Review;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    /** 남의 리뷰는 없는 리뷰와 똑같이 비어 있다. */
    Optional<Review> findByIdAndMemberId(Long id, Long memberId);
}
