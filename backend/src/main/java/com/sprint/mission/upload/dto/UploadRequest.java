package com.sprint.mission.upload.dto;

import com.sprint.mission.upload.domain.UploadPurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UploadRequest(
        @NotNull(message = "업로드 용도를 입력해 주세요.")
        UploadPurpose purpose,
        @NotNull(message = "이미지 형식을 입력해 주세요.")
        @Pattern(regexp = "image/(jpeg|png|webp)", message = "이미지는 JPEG, PNG, WebP만 올릴 수 있습니다.")
        String contentType,
        @NotNull(message = "파일 크기를 입력해 주세요.")
        @Min(value = 1, message = "파일 크기는 1바이트~5MB여야 합니다.")
        @Max(value = 5_242_880, message = "파일 크기는 1바이트~5MB여야 합니다.")
        Long size) {
}
