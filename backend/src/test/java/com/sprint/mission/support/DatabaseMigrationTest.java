package com.sprint.mission.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseMigrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void pg_trgm_확장이_설치된다() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void flyway가_V1을_적용한다() {
        String version = jdbcTemplate.queryForObject(
                "select max(version) from flyway_schema_history where success", String.class);

        assertThat(version).isEqualTo("1");
    }
}
