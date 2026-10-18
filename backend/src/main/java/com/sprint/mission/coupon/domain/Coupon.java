package com.sprint.mission.coupon.domain;

import com.sprint.mission.global.common.BaseEntity;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Grade;
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
 * 선착순 다운로드 쿠폰이다. 발급 기간은 {@code [issueStartsAt, issueEndsAt)}, 사용 기간은
 * {@code [useStartsAt, useEndsAt)}다. 정의가 규칙에 맞지 않으면 DB CHECK에 닿기 전에 {@link #create}가 막는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    static final long FIXED_MIN = 100;
    static final long FIXED_MAX = 1_000_000;
    static final long RATE_MIN = 1;
    static final long RATE_MAX = 90;
    static final int QUANTITY_MAX = 1_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DiscountType discountType;

    @Column(nullable = false)
    private long discountValue;

    private Long maxDiscount;

    @Column(nullable = false)
    private long minOrderAmount;

    @Column(nullable = false)
    private int totalQuantity;

    /**
     * 지금까지 발급된 수량이다. 읽기 전용 매핑이라 엔티티를 저장해도 쓰이지 않는다.
     * {@code CouponRepository.increaseIssuedQuantity}의 조건부 UPDATE로만 늘어나므로, 그 트랜잭션 안에서는 낡은 값일 수 있다.
     */
    @Column(insertable = false, updatable = false)
    private int issuedQuantity;

    /** null이면 누구나 받을 수 있다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Grade requiredGrade;

    @Column(nullable = false)
    private Instant issueStartsAt;

    @Column(nullable = false)
    private Instant issueEndsAt;

    @Column(nullable = false)
    private Instant useStartsAt;

    @Column(nullable = false)
    private Instant useEndsAt;

    /** 규칙에 맞지 않으면 {@code INVALID_INPUT}이다. name은 호출한 쪽에서 앞뒤 공백을 지운 값이다. */
    public static Coupon create(String name, DiscountType discountType, long discountValue, Long maxDiscount,
            long minOrderAmount, int totalQuantity, Grade requiredGrade, Instant issueStartsAt, Instant issueEndsAt,
            Instant useStartsAt, Instant useEndsAt) {
        require(name != null && !name.isBlank() && name.length() <= 50, "쿠폰 이름은 1~50자여야 합니다.");
        require(discountType != null, "할인 방식을 입력해 주세요.");
        if (discountType == DiscountType.RATE) {
            require(discountValue >= RATE_MIN && discountValue <= RATE_MAX, "정률 쿠폰의 할인율은 1~90%여야 합니다.");
            require(maxDiscount != null && maxDiscount > 0, "정률 쿠폰은 최대 할인 금액이 필요합니다.");
        } else {
            require(discountValue >= FIXED_MIN && discountValue <= FIXED_MAX,
                    "정액 쿠폰의 할인 금액은 100~1,000,000원이어야 합니다.");
            require(maxDiscount == null, "정액 쿠폰에는 최대 할인 금액을 둘 수 없습니다.");
        }
        require(minOrderAmount >= 0, "최소 주문금액은 0원 이상이어야 합니다.");
        require(totalQuantity >= 1 && totalQuantity <= QUANTITY_MAX, "발급 수량은 1~1,000,000이어야 합니다.");
        require(issueStartsAt != null && issueEndsAt != null && issueEndsAt.isAfter(issueStartsAt),
                "발급 종료 시각은 시작 시각보다 늦어야 합니다.");
        require(useStartsAt != null && useEndsAt != null && useEndsAt.isAfter(useStartsAt),
                "사용 종료 시각은 시작 시각보다 늦어야 합니다.");
        // 발급이 열려 있는 동안 받은 쿠폰이 이미 사용 기간이 끝나 있으면 안 된다.
        require(!useEndsAt.isBefore(issueEndsAt), "사용 종료 시각은 발급 종료 시각보다 빠를 수 없습니다.");

        Coupon coupon = new Coupon();
        coupon.name = name;
        coupon.discountType = discountType;
        coupon.discountValue = discountValue;
        coupon.maxDiscount = maxDiscount;
        coupon.minOrderAmount = minOrderAmount;
        coupon.totalQuantity = totalQuantity;
        coupon.requiredGrade = requiredGrade;
        coupon.issueStartsAt = issueStartsAt;
        coupon.issueEndsAt = issueEndsAt;
        coupon.useStartsAt = useStartsAt;
        coupon.useEndsAt = useEndsAt;
        return coupon;
    }

    /** 발급 기간 안이고 등급이 충분한가. 수량과 중복 발급은 서비스가 DB 문장으로 판단한다. */
    public boolean isIssuable(Instant now, Grade grade) {
        return isInIssuePeriod(now) && isEligible(grade);
    }

    public boolean isInIssuePeriod(Instant now) {
        return !now.isBefore(issueStartsAt) && now.isBefore(issueEndsAt);
    }

    /** 등급 조건을 채우는가. 등급 제한이 없으면 누구나 true다. */
    public boolean isEligible(Grade grade) {
        return requiredGrade == null || grade.isAtLeast(requiredGrade);
    }

    public boolean isUsable(Instant now) {
        return !now.isBefore(useStartsAt) && now.isBefore(useEndsAt);
    }

    public CouponTerms terms() {
        return new CouponTerms(discountType, discountValue, maxDiscount, minOrderAmount);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
    }
}
