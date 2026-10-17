package com.sprint.mission.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BaseEntityAuditingTest extends IntegrationTest {

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
}
