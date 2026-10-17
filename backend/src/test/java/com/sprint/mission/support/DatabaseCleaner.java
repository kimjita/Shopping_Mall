package com.sprint.mission.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        List<String> tables = jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
                String.class);
        if (tables.isEmpty()) {
            return;
        }
        String quoted = String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList());
        jdbcTemplate.execute("TRUNCATE " + quoted + " RESTART IDENTITY CASCADE");
    }
}
