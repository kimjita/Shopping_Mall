package com.sprint.mission.banner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 관리자 배너 등록·수정 요청이다. 형식은 Bean Validation이, 제목 길이(앞뒤 공백을 지운 값 1~50자)와 기간 선후, 연도 범위(2000~9999)는
 * {@code BannerService}가 검증한다.
 */
public record BannerRequest(
        @NotBlank(message = "배너 제목을 입력해 주세요.")
        @Size(max = 50, message = "배너 제목은 50자 이하여야 합니다.")
        String title,
        @NotBlank(message = "이미지 URL을 입력해 주세요.")
        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^https?://.+", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.")
        String imageUrl,
        @NotBlank(message = "링크 URL을 입력해 주세요.")
        @Size(max = 500, message = "링크 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^(https?://|/(?![/\\\\])).+",
                message = "링크 URL은 http://, https://로 시작하거나 /로 시작하는 서비스 안 경로여야 합니다.")
        String linkUrl,
        @NotNull(message = "노출 시작 시각을 입력해 주세요.")
        Instant startsAt,
        @NotNull(message = "노출 종료 시각을 입력해 주세요.")
        Instant endsAt,
        @Min(value = 0, message = "정렬 순서는 0~1000이어야 합니다.")
        @Max(value = 1000, message = "정렬 순서는 0~1000이어야 합니다.")
        int sortOrder) {
}
