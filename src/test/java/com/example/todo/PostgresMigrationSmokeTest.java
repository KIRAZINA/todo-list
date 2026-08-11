package com.example.todo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the PostgreSQL path (R1).
 *
 * <p>Proves the Flyway migrations (V1 + V2) apply cleanly against a real
 * PostgreSQL instance and that Hibernate's {@code ddl-auto: validate} accepts
 * the PG-generated schema. The H2-based test suite cannot surface PG dialect
 * drift (identity columns, timestamp handling, reserved words), which is the
 * exact failure mode that once silently broke the default H2 profile.
 *
 * <p>Uses the {@code test} profile (Flyway enabled) with {@code @ServiceConnection}
 * overriding the datasource to the container. Skipped automatically when Docker
 * is unavailable; CI should make it mandatory (see .github/workflows/ci.yml).
 *
 * <p>The test JVM runs pinned to UTC via surefire's {@code -Duser.timezone=UTC}
 * (see pom.xml). pgjdbc sends the JVM default timezone as the PostgreSQL
 * {@code TimeZone} startup parameter, which {@code postgres:16} rejects when the
 * host JVM reports the legacy {@code Europe/Kiev} name (renamed to
 * {@code Europe/Kyiv} in tzdata 2022b); there is no pgjdbc connection property
 * that overrides it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesV1AndV2OnRealPostgres() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version IN ('1', '2')",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void entitySchemaValidatesAgainstPostgresGeneratedDdl() {
        // The real assertion is that the context booted: ddl-auto=validate would
        // have failed startup on any entity/schema mismatch. These queries just
        // confirm the expected tables actually exist in the PG container.
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("tasks")).isTrue();
        assertThat(tableExists("revoked_tokens")).isTrue();
        assertThat(tableExists("flyway_schema_history")).isTrue();
    }

    @Test
    void crudRoundTripWorksOnPostgres() {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)",
                "pguser", "$2a$10$abcdefghijklmnopqrstuvwx", "pg@example.com", "USER");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'pguser'", Integer.class);
        assertThat(count).isEqualTo(1);

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'pguser'", Long.class);
        assertThat(id).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO tasks (title, priority, status, user_id) VALUES (?, ?, ?, ?)",
                "PG task", "MEDIUM", "TODO", id);
        Integer taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE user_id = ?", Integer.class, id);
        assertThat(taskCount).isEqualTo(1);
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, table);
        return count != null && count == 1;
    }
}
