package com.sprint.mission.coupon.dto;

import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.global.validation.InstantRange;
import com.sprint.mission.member.domain.Grade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/**
 * 관리자 쿠폰 등록 요청이다. 널·범위는 Bean Validation이, 이름 길이(앞뒤 공백을 지운 값 1~50자)와 할인 방식별 규칙(정률 1~90%와 최대 할인 금액,
 * 정액 100~1,000,000원)과 기간 선후는 {@code Coupon.create}가 검증한다.
 */
public record CouponCreateRequest(
        @NotBlank(message = "쿠폰 이름을 입력해 주세요.")
        String name,
        @NotNull(message = "할인 방식을 입력해 주세요.")
        DiscountType discountType,
        @NotNull(message = "할인 값을 입력해 주세요.")
        Long discountValue,
        Long maxDiscount,
        @NotNull(message = "최소 주문금액을 입력해 주세요.")
        @PositiveOrZero(message = "최소 주문금액은 0원 이상이어야 합니다.")
        Long minOrderAmount,
        @NotNull(message = "발급 수량을 입력해 주세요.")
        @Min(value = 1, message = "발급 수량은 1~1,000,000이어야 합니다.")
        @Max(value = 1_000_000, message = "발급 수량은 1~1,000,000이어야 합니다.")
        Integer totalQuantity,
        Grade requiredGrade,
        @NotNull(message = "발급 시작 시각을 입력해 주세요.")
        @InstantRange
        Instant issueStartsAt,
        @NotNull(message = "발급 종료 시각을 입력해 주세요.")
        @InstantRange
        Instant issueEndsAt,
        @NotNull(message = "사용 시작 시각을 입력해 주세요.")
        @InstantRange
        Instant useStartsAt,
        @NotNull(message = "사용 종료 시각을 입력해 주세요.")
        @InstantRange
        Instant useEndsAt) {
}
