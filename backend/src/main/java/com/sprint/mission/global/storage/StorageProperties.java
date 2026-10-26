package com.sprint.mission.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 저장소(S3) 설정이다. {@code endpoint}가 비면 AWS 기본 엔드포인트를, {@code accessKey}가 비면 AWS 기본 자격 증명 체인을 쓴다.
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String bucket, String region, String endpoint, String publicBaseUrl,
                                boolean pathStyle, String accessKey, String secretKey) {
}
