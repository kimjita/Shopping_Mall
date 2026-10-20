package com.sprint.mission.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TossPropertiesTest {

    @Test
    void toString에_비밀_키가_나오지_않는다() {
        TossProperties properties = new TossProperties("test_sk_secret", URI.create("https://api.tosspayments.com"),
                Duration.ofSeconds(3), Duration.ofSeconds(10));

        assertThat(properties.toString()).doesNotContain("test_sk_secret").contains("secretKey=****")
                .contains("https://api.tosspayments.com");
    }
}
