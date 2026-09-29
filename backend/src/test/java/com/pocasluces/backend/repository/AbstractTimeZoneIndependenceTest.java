package com.pocasluces.backend.repository;

import com.pocasluces.backend.dto.EnelApiFeatureWithEvidence;
import com.pocasluces.backend.dto.EnelApiResponse;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.service.DistrictLocator;
import com.pocasluces.backend.service.EnelApiService;
import com.pocasluces.backend.service.NeighborhoodLocator;
import com.pocasluces.backend.service.OutageDataScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the timezone contract (see backend/README.md, "Timezone contract"): every
 * datetime is stored and returned as Europe/Madrid wall-clock, exactly as Endesa
 * publishes it, no matter what the JVM default time zone is and no matter which path
 * touches the row (native JDBC upsert/read or JPA/JPQL).
 *
 * <p>Each scenario runs once per JVM default zone, set with {@link TimeZone#setDefault}
 * inside the test. Whole-suite runs additionally happen under two JVM zones via
 * surefire ({@code pom.xml}). Concrete subclasses bind this to H2 and to a real
 * PostgreSQL (Testcontainers), because production writes go through the PostgreSQL-only
 * {@code INSERT ... ON CONFLICT} path.</p>
 *
 * <p>Regression for the 2026-09-29 production incident: under a UTC JVM with
 * {@code hibernate.jdbc.time_zone=Europe/Madrid}, the natively upserted
 * {@code fetched_at} (wall-clock) was compared against a Hibernate-bound {@code :now}
 * shifted by +2h, so every outage was resolved in the very run that upserted it and
 * {@code /live} returned nothing, while JPA reads returned Endesa's times shifted -2h.</p>
 *
 * <p>The Spring test annotations live here, not on the subclasses: the transactional test
 * attribute is resolved against the class that declares each test method.</p>
 */
@DataJpaTest
@ActiveProfiles("dev")
@Import(EnelOutageRepositoryImpl.class)
abstract class AbstractTimeZoneIndependenceTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Endesa's feed value {@code "29/09/2026 10:59"}: Europe/Madrid wall-clock. */
    static final LocalDateTime ENDESA_START = LocalDateTime.of(2026, 9, 29, 10, 59);
    /** The scheduler run that fetched it, from the Europe/Madrid {@link Clock}. */
    static final LocalDateTime RUN_NOW = LocalDateTime.of(2026, 9, 29, 11, 0);

    static final double LAT = 37.38797513;
    static final double LON = -5.99567438;

    private static final DateTimeFormatter SQL_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    protected EnelOutageRepository repository;

    @Autowired
    protected TestEntityManager em;

    @Autowired
    protected JdbcTemplate jdbc;

    private TimeZone originalZone;

    static Stream<String> jvmZones() {
        // UTC (production and CI), Madrid (the data's own zone), a zone west of it, a
        // half-hour-offset zone and a zone that is a day ahead of Madrid for part of the day.
        return Stream.of("UTC", "Europe/Madrid", "America/New_York", "Asia/Kolkata", "Pacific/Auckland");
    }

    static Stream<Arguments> wallClocksMissingFromJvmZone() {
        // Local times that do not exist in the JVM zone because DST starts there at that
        // instant. Any conversion through the JVM zone (Timestamp.valueOf, atZone(...))
        // silently moves them one hour later; storing wall-clock verbatim must not.
        return Stream.of(
            Arguments.of("America/New_York", LocalDateTime.of(2026, 3, 8, 2, 30)),
            Arguments.of("Europe/Madrid", LocalDateTime.of(2026, 3, 29, 2, 30))
        );
    }

    @BeforeEach
    void rememberJvmZone() {
        originalZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreJvmZone() {
        TimeZone.setDefault(originalZone);
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void resolveStepMustNotResolveOutagesUpsertedInTheSameRun(String zone) {
        inJvmZone(zone);
        repository.upsert(endesaOutage("A", ENDESA_START, RUN_NOW));
        flushAndClear();

        int resolved = repository.resolveStaleActiveOutages(RUN_NOW);
        flushAndClear();

        assertThat(resolved).isZero();
        assertThat(repository.findCurrentlyActive(RUN_NOW, RUN_NOW.minusHours(6)))
            .singleElement()
            .satisfies(o -> {
                assertThat(o.isActive()).isTrue();
                assertThat(o.getResolvedAt()).isNull();
                assertThat(o.getInterruptionDate()).isEqualTo(ENDESA_START);
                assertThat(o.getFetchedAt()).isEqualTo(RUN_NOW);
            });
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void resolveStepResolvesOnlyRowsFetchedByEarlierRuns(String zone) {
        inJvmZone(zone);
        LocalDateTime previousRun = RUN_NOW.minusMinutes(5);
        repository.upsert(endesaOutage("stale", ENDESA_START.minusHours(1), previousRun));
        repository.upsert(endesaOutage("fresh", ENDESA_START, RUN_NOW));
        flushAndClear();

        int resolved = repository.resolveStaleActiveOutages(RUN_NOW);
        flushAndClear();

        assertThat(resolved).isEqualTo(1);
        assertThat(repository.findByObjectId("stale")).get().satisfies(o -> {
            assertThat(o.isActive()).isFalse();
            assertThat(o.getResolvedAt()).isEqualTo(previousRun);
        });
        assertThat(repository.findByObjectId("fresh")).get().satisfies(o -> {
            assertThat(o.isActive()).isTrue();
            assertThat(o.getResolvedAt()).isNull();
        });
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void nativeWriteIsReadBackVerbatimByEveryReadPath(String zone) {
        inJvmZone(zone);
        repository.upsert(endesaOutage("A", ENDESA_START, RUN_NOW));
        flushAndClear();

        assertStoredAndReadBackVerbatim("A");
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void jpaWriteIsReadBackVerbatimByEveryReadPath(String zone) {
        inJvmZone(zone);
        em.persist(endesaOutage("A", ENDESA_START, RUN_NOW));
        flushAndClear();

        assertStoredAndReadBackVerbatim("A");
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void nativeRowSavedAgainThroughJpaKeepsItsWallClock(String zone) {
        // The DistrictBackfillRunner path: load through JPA, change one field, save().
        inJvmZone(zone);
        repository.upsert(endesaOutage("A", ENDESA_START, RUN_NOW));
        flushAndClear();

        EnelOutage loaded = repository.findByObjectId("A").orElseThrow();
        loaded.setDistrictName("Casco Antiguo");
        repository.save(loaded);
        flushAndClear();

        assertStoredAndReadBackVerbatim("A");
        assertThat(repository.findByObjectId("A")).get()
            .extracting(EnelOutage::getDistrictName).isEqualTo("Casco Antiguo");
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void schedulerRunKeepsJustFetchedOutageActiveAndResolvesItOnceItDisappears(String zone) {
        inJvmZone(zone);
        EnelApiService api = mock(EnelApiService.class);
        NeighborhoodLocator neighborhoods = mock(NeighborhoodLocator.class);
        DistrictLocator districts = mock(DistrictLocator.class);
        when(neighborhoods.findNeighborhood(anyDouble(), anyDouble())).thenReturn("Casco Antiguo");
        when(districts.findDistrict(anyDouble(), anyDouble(), any())).thenReturn("Casco Antiguo");
        when(api.fetchSevillaOutages()).thenReturn(List.of(endesaFeed("4711", "29/09/2026 10:59")));

        scheduler(api, neighborhoods, districts, RUN_NOW).fetchAndSaveOutages();
        flushAndClear();

        assertThat(storedText("interruption_date", "4711")).startsWith(sqlText(ENDESA_START));
        assertThat(storedText("fetched_at", "4711")).startsWith(sqlText(RUN_NOW));
        assertThat(repository.findCurrentlyActive(RUN_NOW, RUN_NOW.minusHours(6)))
            .singleElement()
            .satisfies(o -> {
                assertThat(o.isActive()).isTrue();
                assertThat(o.getResolvedAt()).isNull();
                assertThat(o.getInterruptionDate()).isEqualTo(ENDESA_START);
                assertThat(o.getFetchedAt()).isEqualTo(RUN_NOW);
            });
        assertThat(repository.findByYearAndMonth(2026, 9))
            .singleElement()
            .extracting(EnelOutage::getInterruptionDate).isEqualTo(ENDESA_START);

        // Next poll, five minutes later: Endesa no longer publishes it.
        when(api.fetchSevillaOutages()).thenReturn(List.of());
        LocalDateTime nextRun = RUN_NOW.plusMinutes(5);
        scheduler(api, neighborhoods, districts, nextRun).fetchAndSaveOutages();
        flushAndClear();

        assertThat(repository.findCurrentlyActive(nextRun, nextRun.minusHours(6))).isEmpty();
        assertThat(repository.findByObjectId("4711")).get().satisfies(o -> {
            assertThat(o.isActive()).isFalse();
            assertThat(o.getResolvedAt()).isEqualTo(RUN_NOW);
            assertThat(o.getInterruptionDate()).isEqualTo(ENDESA_START);
        });
    }

    @ParameterizedTest(name = "JVM zone {0}, wall-clock {1}")
    @MethodSource("wallClocksMissingFromJvmZone")
    void wallClockThatDoesNotExistInTheJvmZoneIsStillStoredVerbatim(String zone, LocalDateTime wallClock) {
        inJvmZone(zone);
        EnelOutage viaNative = endesaOutage("native", wallClock, wallClock.plusMinutes(1));
        EnelOutage viaJpa = endesaOutage("jpa", wallClock, wallClock.plusMinutes(1));
        viaJpa.setLatitude(LAT + 0.01);
        repository.upsert(viaNative);
        em.persist(viaJpa);
        flushAndClear();

        for (String objectId : List.of("native", "jpa")) {
            assertThat(storedText("interruption_date", objectId)).startsWith(sqlText(wallClock));
            assertThat(storedText("fetched_at", objectId)).startsWith(sqlText(wallClock.plusMinutes(1)));
            assertThat(repository.findByObjectId(objectId)).get()
                .extracting(EnelOutage::getInterruptionDate).isEqualTo(wallClock);
        }
        assertThat(repository.findCurrentlyActive(wallClock.plusMinutes(2), wallClock.minusHours(6)))
            .extracting(EnelOutage::getInterruptionDate)
            .containsExactly(wallClock, wallClock);
    }

    private void assertStoredAndReadBackVerbatim(String objectId) {
        // What PostgreSQL/H2 actually hold, independent of any JDBC datetime conversion.
        assertThat(storedText("interruption_date", objectId)).startsWith(sqlText(ENDESA_START));
        assertThat(storedText("fetched_at", objectId)).startsWith(sqlText(RUN_NOW));

        // Native JDBC read (the /live endpoint).
        assertThat(repository.findCurrentlyActive(RUN_NOW, RUN_NOW.minusHours(6)))
            .singleElement()
            .satisfies(o -> {
                assertThat(o.getInterruptionDate()).isEqualTo(ENDESA_START);
                assertThat(o.getFetchedAt()).isEqualTo(RUN_NOW);
            });

        // JPA/JPQL reads (the /monthly, /yearly and /enel endpoints).
        assertThat(repository.findByYearAndMonth(2026, 9))
            .singleElement()
            .extracting(EnelOutage::getInterruptionDate).isEqualTo(ENDESA_START);
        assertThat(repository.findAllByOrderByInterruptionDateDesc())
            .singleElement()
            .extracting(EnelOutage::getFetchedAt).isEqualTo(RUN_NOW);
        assertThat(repository.findByLatitudeAndLongitudeAndInterruptionDateAndServiceType(LAT, LON, ENDESA_START, "AT"))
            .isPresent();
    }

    private OutageDataScheduler scheduler(EnelApiService api, NeighborhoodLocator neighborhoods,
                                          DistrictLocator districts, LocalDateTime madridNow) {
        Clock clock = Clock.fixed(madridNow.atZone(MADRID).toInstant(), MADRID);
        return new OutageDataScheduler(api, repository, neighborhoods, districts, clock,
            new com.pocasluces.backend.service.FetchHealthTracker(clock));
    }

    private String storedText(String column, String objectId) {
        return jdbc.queryForObject(
            "SELECT CAST(" + column + " AS VARCHAR) FROM enel_outages WHERE object_id = ?",
            String.class, objectId);
    }

    private static String sqlText(LocalDateTime wallClock) {
        return wallClock.format(SQL_TEXT);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static void inJvmZone(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));
    }

    static EnelOutage endesaOutage(String objectId, LocalDateTime interruptionDate, LocalDateTime fetchedAt) {
        return EnelOutage.builder()
            .objectId(objectId)
            .latitude(LAT)
            .longitude(LON)
            .affectedClients(120)
            .serviceType("AT")
            .interruptionDate(interruptionDate)
            .neighborhoodName("Casco Antiguo")
            .cause("Avería")
            .firstSeenAt(fetchedAt)
            .fetchedAt(fetchedAt)
            .createdAt(fetchedAt)
            .updatedAt(fetchedAt)
            .build();
    }

    private static EnelApiFeatureWithEvidence endesaFeed(String objectId, String interruptionDate) {
        EnelApiResponse.Attributes attr = new EnelApiResponse.Attributes();
        attr.setObjectId(objectId);
        attr.setLatitude(LAT);
        attr.setLongitude(LON);
        attr.setAffectedClient(120);
        attr.setInterruptionDate(interruptionDate);
        attr.setServiceType("AT");
        attr.setCause("Avería");
        EnelApiResponse.Feature feature = new EnelApiResponse.Feature();
        feature.setAttributes(attr);
        String fragment = "{\"attributes\":{\"objectid1\":\"" + objectId + "\",\"latitude\":" + LAT
            + ",\"longitude\":" + LON + ",\"interruption_date\":\"" + interruptionDate + "\"}}";
        return new EnelApiFeatureWithEvidence(feature, "https://dpa-portalgis.enel.com/.../query?resultOffset=0", fragment);
    }
}
