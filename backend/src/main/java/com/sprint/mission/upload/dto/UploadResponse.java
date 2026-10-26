package com.sprint.mission.upload.dto;

import java.time.Instant;

public record UploadResponse(String uploadUrl, String imageUrl, Instant expiresAt) {
}
