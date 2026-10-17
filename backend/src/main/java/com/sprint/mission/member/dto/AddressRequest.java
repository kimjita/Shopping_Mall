package com.sprint.mission.member.dto;

import com.sprint.mission.member.domain.PhonePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "받는 사람은 1~30자여야 합니다.")
        @Size(max = 30, message = "받는 사람은 1~30자여야 합니다.")
        String recipient,

        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = PhonePolicy.REGEX, message = PhonePolicy.MESSAGE)
        String phone,

        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
        String zipCode,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address1,

        @Size(max = 200, message = "상세 주소는 200자 이하여야 합니다.")
        String address2,

        boolean isDefault) {
}
