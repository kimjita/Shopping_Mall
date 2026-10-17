package com.sprint.mission.member.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
