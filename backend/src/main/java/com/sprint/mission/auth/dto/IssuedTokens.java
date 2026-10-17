package com.sprint.mission.auth.dto;

public record IssuedTokens(String accessToken, long expiresIn, String refreshToken) {
}
