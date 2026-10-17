package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.PhonePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "이름은 1~30자여야 합니다.")
        @Size(min = 1, max = 30, message = "이름은 1~30자여야 합니다.")
        String name,

        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = PhonePolicy.REGEX, message = PhonePolicy.MESSAGE)
        String phone) {
}
