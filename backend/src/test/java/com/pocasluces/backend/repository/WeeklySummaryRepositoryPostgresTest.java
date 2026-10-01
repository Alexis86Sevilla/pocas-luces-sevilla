package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.WeeklySummaryRepository.DistrictCount;
import com.pocasluces.backend.repository.WeeklySummaryRepository.Totals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs Docker (Testcontainers): runs in CI. Timestamps are pinned to whole seconds (Postgres keeps microseconds). */
@DataJpaTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EnelOutageRepositoryImpl.class, WeeklySummaryRepository.class})
@Testcontainers
class WeeklySummaryRepositoryPostgresTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);
    private static final LocalDateTime FROM = WEEK.atStartOfDay();
    private static final LocalDateTime TO = WEEK.plusDays(7).atStartOfDay();

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
    private EnelOutageRepository outages;

    @Autowired
    private WeeklySummaryRepository repository;

    @Test
    void aggregatesOnlyTheOutagesThatStartedInsideTheHalfOpenWeek() {
        LocalDateTime seen = LocalDateTime.of(2026, 9, 22, 12, 0);
        outages.upsert(outage(1, FROM, "Triana", 10, seen, seen, seen.plusMinutes(5)));                  // brief (Monday 00:00 included)
        outages.upsert(outage(2, FROM.plusDays(2), "Triana", 20, seen, seen.plusMinutes(10), null));     // still active
        outages.upsert(outage(3, TO.minusSeconds(1), "Nervión", null, seen, seen.plusMinutes(10), seen.plusMinutes(10)));
        outages.upsert(outage(4, TO, "Triana", 99, seen, seen, seen));                                   // next Monday 00:00: excluded
        outages.upsert(outage(5, FROM.minusSeconds(1), "Triana", 99, seen, seen, seen));                 // previous Sunday: excluded

        assertThat(repository.totals(FROM, TO)).isEqualTo(new Totals(3, 30, 1));
        assertThat(repository.districtCounts(FROM, TO))
            .containsExactly(new DistrictCount("Triana", 2), new DistrictCount("Nervión", 1));
    }

    @Test
    void totalsOfAnEmptyWindowAreZero() {
        assertThat(repository.totals(FROM, TO)).isEqualTo(new Totals(0, 0, 0));
        assertThat(repository.districtCounts(FROM, TO)).isEmpty();
    }

    @Test
    void reportsTheEarliestFirstSeen() {
        assertThat(repository.earliestFirstSeen()).isNull();

        outages.upsert(outage(1, FROM, "Triana", 1, LocalDateTime.of(2026, 9, 10, 8, 0), LocalDateTime.of(2026, 9, 10, 8, 5), null));
        outages.upsert(outage(2, FROM.plusDays(1), "Triana", 1, LocalDateTime.of(2026, 9, 12, 8, 0), LocalDateTime.of(2026, 9, 12, 8, 5), null));

        assertThat(repository.earliestFirstSeen()).isEqualTo(LocalDateTime.of(2026, 9, 10, 8, 0));
    }

    @Test
    void markingAWeekSentIsInsertIfAbsent() {
        LocalDateTime first = LocalDateTime.of(2026, 9, 28, 9, 5);

        assertThat(repository.isWeekSent(WEEK)).isFalse();
        assertThat(repository.markWeekSent(WEEK, first)).isTrue();
        assertThat(repository.markWeekSent(WEEK, first.plusMinutes(5))).isFalse();

        assertThat(repository.isWeekSent(WEEK)).isTrue();
        assertThat(repository.isWeekSent(WEEK.plusDays(7))).isFalse();
    }

    private EnelOutage outage(int n, LocalDateTime interruptionDate, String district, Integer clients,
                              LocalDateTime firstSeenAt, LocalDateTime fetchedAt, LocalDateTime resolvedAt) {
        return EnelOutage.builder()
            .objectId("weekly-" + n)
            .interruptionDate(interruptionDate)
            .serviceType("GB")
            .districtName(district)
            .neighborhoodName("San Pablo")
            .affectedClients(clients)
            .latitude((double) n)
            .longitude((double) n)
            .active(resolvedAt == null)
            .resolvedAt(resolvedAt == null ? null : fetchedAt)
            .firstSeenAt(firstSeenAt)
            .fetchedAt(fetchedAt)
            .createdAt(firstSeenAt)
            .updatedAt(fetchedAt)
            .build();
    }
}
