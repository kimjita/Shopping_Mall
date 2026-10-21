package com.sprint.mission.banner.repository;

import com.sprint.mission.banner.domain.Banner;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findAllByOrderBySortOrderAscIdAsc();

    /** {@code startsAt <= now < endsAt}인 배너다. */
    List<Banner> findByStartsAtLessThanEqualAndEndsAtGreaterThanOrderBySortOrderAscIdAsc(Instant startsAt,
            Instant endsAt);
}
