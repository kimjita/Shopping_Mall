package com.sprint.mission.banner.dto;

import com.sprint.mission.banner.domain.Banner;
import java.time.Instant;

public record BannerResponse(Long id, String title, String imageUrl, String linkUrl, Instant startsAt,
        Instant endsAt, int sortOrder) {

    public static BannerResponse from(Banner banner) {
        return new BannerResponse(banner.getId(), banner.getTitle(), banner.getImageUrl(), banner.getLinkUrl(),
                banner.getStartsAt(), banner.getEndsAt(), banner.getSortOrder());
    }
}
