package br.com.systec.hive.integrationtest.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reset() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT LIKE 'flyway_schema_history%'",
                String.class);
        if (tables.isEmpty()) {
            return;
        }
        String safeNames = tables.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + safeNames + " RESTART IDENTITY CASCADE");
    }
}
