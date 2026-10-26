package com.sprint.mission.point.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 관리자 적립금 지급 요청이다. */
public record AdminPointGrantRequest(
        @Min(value = 1, message = "지급 금액은 1~1,000,000원이어야 합니다.")
        @Max(value = 1_000_000, message = "지급 금액은 1~1,000,000원이어야 합니다.")
        long amount,
        @NotBlank(message = "지급 사유를 입력해 주세요.")
        // @NotBlank는 전각 공백 같은 유니코드 공백을 걸러내지 못해 따로 검사한다. 저장 전에 strip()하므로 공백만 있으면 안 된다.
        @Pattern(regexp = "(?s)(?U).*\\S.*", message = "지급 사유를 입력해 주세요.")
        // 줄바꿈·탭을 포함한 모든 제어 문자(\p{Cntrl}, U+0000~U+001F와 U+007F)를 거부한다.
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "지급 사유에 쓸 수 없는 문자가 있습니다.")
        @Size(max = 100, message = "지급 사유는 100자 이하여야 합니다.")
        String reason) {
}
