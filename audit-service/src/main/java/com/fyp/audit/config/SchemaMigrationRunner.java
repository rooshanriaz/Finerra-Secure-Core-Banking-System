package com.fyp.audit.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures legacy MySQL schema matches current entity lengths.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE audit_records MODIFY COLUMN fabric_tx_id VARCHAR(255)");
            log.info("Ensured audit_records.fabric_tx_id supports full Fabric transaction IDs");
        } catch (Exception ex) {
            log.warn("Schema migration skipped or failed: {}", ex.getMessage());
        }
    }
}
