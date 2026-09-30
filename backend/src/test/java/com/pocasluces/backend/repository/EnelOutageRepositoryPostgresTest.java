package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("dev")
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

    @Autowired
    private TestEntityManager entityManager;

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
    void shouldResolveStaleActiveOutagesAgainstRealPostgres() {
        LocalDateTime staleFetch = LocalDateTime.of(2026, 7, 10, 7, 55);
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 8, 0);

        EnelOutage stale = outage("1", LocalDateTime.of(2026, 7, 10, 6, 0));
        stale.setFetchedAt(staleFetch);
        EnelOutage fresh = outage("2", LocalDateTime.of(2026, 7, 10, 7, 0));
        fresh.setFetchedAt(now);
        repository.upsert(stale);
        repository.upsert(fresh);

        int resolved = repository.resolveStaleActiveOutages(now);

        assertThat(resolved).isEqualTo(1);
        List<EnelOutage> all = repository.findAll();
        assertThat(all).filteredOn(o -> o.getObjectId().equals("1"))
            .allSatisfy(o -> {
                assertThat(o.isActive()).isFalse();
                assertThat(o.getResolvedAt()).isEqualTo(staleFetch);
            });
        assertThat(all).filteredOn(o -> o.getObjectId().equals("2"))
            .allSatisfy(o -> {
                assertThat(o.isActive()).isTrue();
                assertThat(o.getResolvedAt()).isNull();
            });
    }

    @Test
    void shouldClearResolvedAtWhenOutageReappearsViaAtomicUpsert() {
        EnelOutage original = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        original.setActive(false);
        original.setResolvedAt(LocalDateTime.of(2026, 7, 10, 9, 0));
        repository.upsert(original);

        // Mirrors the scheduler's re-open path via the atomic ON CONFLICT upsert: a fresh
        // EnelOutage for the same location key, with resolvedAt at its builder default
        // (null) and active defaulting to true, must clear the previous resolution.
        EnelOutage reopened = outage("1-reopened", LocalDateTime.of(2026, 7, 10, 8, 30));
        repository.upsert(reopened);

        List<EnelOutage> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).isActive()).isTrue();
        assertThat(all.get(0).getResolvedAt()).isNull();
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

    @Test
    void shouldTreatDifferentCoordinatesAsDistinctOutagesEvenWithSameNeighborhoodAndStart() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 10, 8, 30);

        EnelOutage first = outage("1", start);
        first.setLatitude(37.3970);
        first.setLongitude(-5.9800);
        first.setAffectedClients(50);

        EnelOutage second = outage("2", start);
        second.setLatitude(37.4000);
        second.setLongitude(-5.9850);
        second.setAffectedClients(120);

        repository.upsert(first);
        repository.upsert(second);

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findAll())
            .extracting(EnelOutage::getAffectedClients)
            .containsExactlyInAnyOrder(50, 120);
    }

    @Test
    void upsertPreservesAnnouncementStateAndResetsOnlyMissingPollsWhenTheOutageReappears() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 10, 8, 30);
        LocalDateTime firstFetch = LocalDateTime.of(2026, 7, 10, 9, 0);
        LocalDateTime announcedAt = LocalDateTime.of(2026, 7, 10, 9, 5);
        LocalDateTime restorationAnnouncedAt = LocalDateTime.of(2026, 7, 10, 10, 0);
        LocalDateTime laterFetch = LocalDateTime.of(2026, 7, 10, 11, 0);

        // A row that predates the alerts (announce_eligible = false) must stay ineligible forever.
        EnelOutage original = outage("1", start);
        original.setFetchedAt(firstFetch);
        original.setAnnounceEligible(false);
        repository.upsert(original);
        Long id = repository.findAll().get(0).getId();
        repository.markAnnounced(List.of(id), announcedAt);
        repository.markRestorationAnnounced(List.of(id), restorationAnnouncedAt);

        // Fresh poll, same identity key: a new object with builder defaults (eligible = true,
        // no marks, missing_polls = 0) must not overwrite the announcement state.
        EnelOutage resighted = outage("1-resighted", start);
        resighted.setFetchedAt(laterFetch);
        resighted.setCause("Avería");
        repository.upsert(resighted);

        entityManager.clear();
        List<EnelOutage> all = repository.findAll();
        assertThat(all).hasSize(1);
        EnelOutage row = all.get(0);
        assertThat(row.getId()).isEqualTo(id);
        assertThat(row.isAnnounceEligible()).isFalse();
        assertThat(row.getAnnouncedAt()).isEqualTo(announcedAt);
        assertThat(row.getRestorationAnnouncedAt()).isEqualTo(restorationAnnouncedAt);
        assertThat(row.getFirstSeenAt()).isEqualTo(original.getFirstSeenAt());
        assertThat(row.getObjectId()).isEqualTo("1-resighted");
        assertThat(row.getFetchedAt()).isEqualTo(laterFetch);
        assertThat(row.getCause()).isEqualTo("Avería");
    }

    @Test
    void upsertResetsMissingPollsToZeroWhenAnAnnouncedOutageReappearsButKeepsItsAnnouncedAt() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 10, 8, 30);
        LocalDateTime announcedAt = LocalDateTime.of(2026, 7, 10, 9, 5);

        EnelOutage original = outage("1", start);
        repository.upsert(original);
        Long id = repository.findAll().get(0).getId();
        repository.markAnnounced(List.of(id), announcedAt);

        // The next two successful polls do not publish it: inactive, missing_polls counts up.
        EnelOutage gone = outage("1", start);
        gone.setActive(false);
        repository.upsert(gone);
        repository.incrementMissingPollsOfAnnouncedInactiveOutages();
        repository.incrementMissingPollsOfAnnouncedInactiveOutages();
        entityManager.clear();
        assertThat(repository.findAll().get(0).getMissingPolls()).isEqualTo(2);

        // It is published again: the upsert resets the counter (intended, see the upsert) and
        // announced_at survives.
        EnelOutage back = outage("1", start);
        back.setFetchedAt(LocalDateTime.of(2026, 7, 10, 11, 0));
        repository.upsert(back);

        entityManager.clear();
        EnelOutage row = repository.findAll().get(0);
        assertThat(row.getMissingPolls()).isZero();
        assertThat(row.isActive()).isTrue();
        assertThat(row.getAnnouncedAt()).isEqualTo(announcedAt);
        assertThat(row.getRestorationAnnouncedAt()).isNull();
        assertThat(row.isAnnounceEligible()).isTrue();
    }

    private EnelOutage outage(String objectId, LocalDateTime interruptionDate) {
        LocalDateTime now = LocalDateTime.now();
        return EnelOutage.builder()
            .objectId(objectId)
            .interruptionDate(interruptionDate)
            .serviceType("GB")
            .neighborhoodName("San Pablo")
            .latitude(0.0)
            .longitude(0.0)
            .fetchedAt(now)
            .firstSeenAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
