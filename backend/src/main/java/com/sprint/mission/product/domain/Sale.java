package com.sprint.mission.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품에 거는 기간 한정 세일이다. 시작 시각은 포함하고 종료 시각은 포함하지 않는다. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale {

    @Column(name = "sale_rate")
    private int rate;

    @Column(name = "sale_starts_at")
    private Instant startsAt;

    @Column(name = "sale_ends_at")
    private Instant endsAt;

    public Sale(int rate, Instant startsAt, Instant endsAt) {
        this.rate = rate;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /** 시작 시각 정각부터 세일이고, 종료 시각 정각에는 세일이 아니다. */
    public boolean isActive(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    /** 할인율을 적용한 가격이다. 10원 단위 미만은 내린다. */
    public long apply(long price) {
        return (price * (100 - rate) / 100) / 10 * 10;
    }
}
