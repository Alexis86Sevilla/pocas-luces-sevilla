package com.pocasluces.backend.repository;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link AbstractTimeZoneIndependenceTest} against a real PostgreSQL, i.e. the production
 * write path ({@code INSERT ... ON CONFLICT} through JDBC) mixed with JPA/JPQL reads and the
 * JPQL bulk resolve step. Needs a running Docker daemon.
 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TimeZoneIndependencePostgresTest extends AbstractTimeZoneIndependenceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pocas_luces_timezone_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
