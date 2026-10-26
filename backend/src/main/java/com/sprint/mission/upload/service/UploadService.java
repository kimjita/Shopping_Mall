package com.sprint.mission.upload.service;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.global.storage.StorageProperties;
import com.sprint.mission.member.domain.Role;
import com.sprint.mission.upload.dto.UploadRequest;
import com.sprint.mission.upload.dto.UploadResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Duration URL_TTL = Duration.ofMinutes(5);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final Clock clock;

    /** 형식·크기를 서명에 넣으므로 클라이언트는 요청한 값 그대로만 올릴 수 있다. */
    public UploadResponse presign(Role role, UploadRequest request) {
        if (request.purpose().adminOnly() && role != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String key = "%s/%s/%s.%s".formatted(
                request.purpose().name().toLowerCase(Locale.ROOT),
                LocalDate.now(clock.withZone(SEOUL)).format(DATE_PATH),
                UUID.randomUUID(),
                extension(request.contentType()));
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presign -> presign
                .signatureDuration(URL_TTL)
                .putObjectRequest(put -> put
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(request.contentType())
                        .contentLength(request.size())));
        // 만료 시각은 서명에 실제로 들어간 값을 그대로 돌려준다.
        return new UploadResponse(presigned.url().toString(), properties.publicBaseUrl() + "/" + key,
                presigned.expiration());
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }
}
