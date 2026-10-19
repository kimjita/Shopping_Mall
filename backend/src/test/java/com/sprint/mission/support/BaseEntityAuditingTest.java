package com.sprint.mission.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class BaseEntityAuditingTest extends IntegrationTest {

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MutableClock clock;

    @Autowired
    AuditedSampleRepository repository;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Test
    void 저장하면_생성_수정_시각이_채워진다() {
        AuditedSample saved = repository.saveAndFlush(new AuditedSample("sample"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 테스트가_끝나면_테이블이_비워진다() {
        repository.saveAndFlush(new AuditedSample("sample"));

        databaseCleaner.clean();

        assertThat(repository.count()).isZero();
    }

    @Test
    void 생성_수정_시각은_Clock_빈을_따른다() {
        Instant fixed = Instant.parse("2030-01-01T00:00:00Z");
        clock.set(fixed);

        AuditedSample saved = repository.saveAndFlush(new AuditedSample("sample"));

        assertThat(saved.getCreatedAt()).isEqualTo(fixed);
        assertThat(saved.getUpdatedAt()).isEqualTo(fixed);
    }
}
