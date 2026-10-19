package com.sprint.mission.coupon.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 받은 쿠폰이다. 행은 {@code CouponRepository}가 아니라 {@code MemberCouponRepository.insertIfAbsent}로만 만든다
 * (1인 1장을 {@code on conflict}로 지키기 위해서다). 사용과 복원은 회원 행을 잠근 트랜잭션 안에서 엔티티 메서드로 한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberCouponStatus status;

    private Long usedOrderId;

    private Instant usedAt;

    /** 주문에 쓴다. 이미 쓴 쿠폰이면 예외다. */
    public void use(Long orderId, Instant now) {
        if (status != MemberCouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용할 수 없는 쿠폰 상태이다: " + status);
        }
        this.status = MemberCouponStatus.USED;
        this.usedOrderId = orderId;
        this.usedAt = now;
    }

    /** 주문이 취소되어 쿠폰을 되돌린다. 사용한 쿠폰이 아니면 예외다. */
    public void restore() {
        if (status != MemberCouponStatus.USED) {
            throw new IllegalStateException("복원할 수 없는 쿠폰 상태이다: " + status);
        }
        this.status = MemberCouponStatus.AVAILABLE;
        this.usedOrderId = null;
        this.usedAt = null;
    }
}
