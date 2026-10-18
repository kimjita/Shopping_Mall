package com.sprint.mission.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    /** 마이그레이션 시드 테이블이라 비우지 않는다. */
    private static final List<String> EXCLUDED_TABLES = List.of("flyway_schema_history", "category");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        List<String> tables = jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public'", String.class).stream()
                .filter(t -> !EXCLUDED_TABLES.contains(t))
                .toList();
        if (tables.isEmpty()) {
            return;
        }
        String quoted = String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList());
        jdbcTemplate.execute("TRUNCATE " + quoted + " RESTART IDENTITY CASCADE");
    }
}
