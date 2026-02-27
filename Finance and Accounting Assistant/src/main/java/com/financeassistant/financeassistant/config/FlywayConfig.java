package com.financeassistant.financeassistant.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FIX for: "Migration checksum mismatch for migration version 2"
 *
 * Flyway stores a checksum of each migration file in the flyway_schema_history
 * table. If you edit a migration file that was already applied to the DB,
 * Flyway refuses to start.
 *
 * This strategy calls repair() BEFORE migrate() on every startup.
 * repair() updates the stored checksums to match the current files,
 * then migrate() runs any pending migrations normally.
 *
 * Safe for DEVELOPMENT. For production, remove this bean and never edit
 * already-applied migrations — create new ones instead.
 *
 * Place at:
 *   src/main/java/com/financeassistant/financeassistant/config/FlywayConfig.java
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            log.info("Flyway: running repair() to fix any checksum mismatches...");
            flyway.repair();
            log.info("Flyway: repair complete. Running migrate()...");
            flyway.migrate();
        };
    }
}
