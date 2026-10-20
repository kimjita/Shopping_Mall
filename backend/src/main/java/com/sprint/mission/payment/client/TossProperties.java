package com.sprint.mission.payment.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 비밀 키가 비어 있어도 시작은 된다(로컬 개발). 호출할 때 비어 있으면 실패한다. */
@ConfigurationProperties("app.toss")
public record TossProperties(String secretKey, URI baseUrl, Duration connectTimeout, Duration readTimeout) {
}
