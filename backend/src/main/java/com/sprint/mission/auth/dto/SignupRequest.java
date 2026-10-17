package com.sprint.mission.auth.dto;

import com.sprint.mission.member.domain.PasswordPolicy;
import com.sprint.mission.member.domain.PhonePolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "이름은 1~30자여야 합니다.")
        @Size(min = 1, max = 30, message = "이름은 1~30자여야 합니다.")
        String name,

        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = PhonePolicy.REGEX, message = PhonePolicy.MESSAGE)
        String phone) {

    /**
     * {@code @Email}은 앞뒤 공백이 있으면 거절하므로, 검증 전에 앞뒤 공백만 지워요. 소문자 정규화와 중복 검사는 서비스에서 해요.
     */
    public SignupRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
