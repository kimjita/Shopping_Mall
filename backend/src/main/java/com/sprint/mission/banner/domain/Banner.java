package com.sprint.mission.banner.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메인 배너다. 노출 기간은 {@code [startsAt, endsAt)}이고, 값 검증은 {@code BannerService}가 한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 500)
    private String linkUrl;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int sortOrder;

    public static Banner create(String title, String imageUrl, String linkUrl, Instant startsAt, Instant endsAt,
            int sortOrder) {
        Banner banner = new Banner();
        banner.update(title, imageUrl, linkUrl, startsAt, endsAt, sortOrder);
        return banner;
    }

    /** 전체 교체다. */
    public void update(String title, String imageUrl, String linkUrl, Instant startsAt, Instant endsAt,
            int sortOrder) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.sortOrder = sortOrder;
    }
}
