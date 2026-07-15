package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EnelOutageRepositoryImpl.class)
@Testcontainers
class EnelOutageRepositoryPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pocas_luces_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private EnelOutageRepository repository;

    @Test
    void shouldFindCurrentlyActiveAgainstRealPostgres() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 12, 0);

        EnelOutage active = outage("1", now.minusHours(2));
        active.setRepositionDate(now.plusHours(2));
        active.setFetchedAt(now.minusMinutes(10));

        repository.upsert(active);

        List<EnelOutage> result = repository.findCurrentlyActive(now, now.minusHours(6));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getObjectId()).isEqualTo("1");
        assertThat(result.get(0).getRepositionDate()).isEqualTo(now.plusHours(2));
        assertThat(result.get(0).isActive()).isTrue();

        EnelOutage update = outage("1-updated", now.minusHours(2));
        update.setRepositionDate(now.plusHours(4));
        update.setFetchedAt(now.minusMinutes(5));

        repository.upsert(update);

        assertThat(repository.count()).isEqualTo(1);
        List<EnelOutage> updatedResult = repository.findCurrentlyActive(now, now.minusHours(6));
        assertThat(updatedResult).hasSize(1);
        assertThat(updatedResult.get(0).getObjectId()).isEqualTo("1-updated");
        assertThat(updatedResult.get(0).getRepositionDate()).isEqualTo(now.plusHours(4));

        EnelOutage inactive = outage("1-updated", now.minusHours(2));
        inactive.setRepositionDate(now.plusHours(4));
        inactive.setFetchedAt(now.minusMinutes(5));
        inactive.setActive(false);

        repository.upsert(inactive);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findCurrentlyActive(now, now.minusHours(6))).isEmpty();
    }

    @Test
    void shouldExcludeInactiveRowsFromCurrentlyActive() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 12, 0);

        EnelOutage active = outage("1", now.minusHours(2));
        active.setRepositionDate(now.plusHours(2));
        active.setFetchedAt(now.minusMinutes(10));

        EnelOutage inactive = outage("2", now.minusHours(1));
        inactive.setRepositionDate(now.plusHours(1));
        inactive.setFetchedAt(now.minusMinutes(5));
        inactive.setActive(false);

        repository.upsert(active);
        repository.upsert(inactive);

        List<EnelOutage> result = repository.findCurrentlyActive(now, now.minusHours(6));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getObjectId()).isEqualTo("1");
    }

    @Test
    void shouldFindByYearAndMonthUsingPostgresDateFunctions() {
        EnelOutage july = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage august = outage("2", LocalDateTime.of(2026, 8, 5, 14, 0));

        repository.upsert(july);
        repository.upsert(august);

        List<EnelOutage> result = repository.findByYearAndMonth(2026, 7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getObjectId()).isEqualTo("1");
        assertThat(result.get(0).getInterruptionDate()).isEqualTo(LocalDateTime.of(2026, 7, 10, 8, 30));
    }

    private EnelOutage outage(String objectId, LocalDateTime interruptionDate) {
        LocalDateTime now = LocalDateTime.now();
        return EnelOutage.builder()
            .objectId(objectId)
            .interruptionDate(interruptionDate)
            .serviceType("GB")
            .neighborhoodName("San Pablo")
            .fetchedAt(now)
            .firstSeenAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
