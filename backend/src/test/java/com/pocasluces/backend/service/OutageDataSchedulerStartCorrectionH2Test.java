package com.pocasluces.backend.service;

import com.pocasluces.backend.dto.EnelApiFeatureWithEvidence;
import com.pocasluces.backend.dto.EnelApiResponse;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.repository.EnelOutageRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression for the Polígono Sur case (audit 2026-10-02) through the real
 * repository on H2: the scheduler, the correction and the upsert/resolve steps, and the
 * Telegram candidate queries. The Postgres upsert path of the same correction is covered by
 * {@code EnelOutageRepositoryPostgresTest} (Docker, CI).
 */
@DataJpaTest
@ActiveProfiles("dev")
@Import(EnelOutageRepositoryImpl.class)
class OutageDataSchedulerStartCorrectionH2Test {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final double LAT = 37.3521;
    private static final double LON = -5.9712;
    private static final LocalDateTime POLL_1 = LocalDateTime.of(2026, 9, 30, 23, 5, 9);
    private static final LocalDateTime POLL_2 = LocalDateTime.of(2026, 9, 30, 23, 10, 9);
    private static final LocalDateTime POLL_3 = LocalDateTime.of(2026, 9, 30, 23, 15, 9);
    private static final LocalDateTime START_A = LocalDateTime.of(2026, 9, 30, 23, 0);
    private static final LocalDateTime START_B = LocalDateTime.of(2026, 9, 30, 22, 50);

    @Autowired
    private EnelOutageRepository repository;

    @Autowired
    private TestEntityManager em;

    private EnelApiService api;
    private MutableClock clock;
    private OutageDataScheduler scheduler;

    @BeforeEach
    void setUp() {
        api = mock(EnelApiService.class);
        NeighborhoodLocator neighborhoods = mock(NeighborhoodLocator.class);
        DistrictLocator districts = mock(DistrictLocator.class);
        when(neighborhoods.findNeighborhood(anyDouble(), anyDouble())).thenReturn("Polígono Sur");
        when(districts.findDistrict(anyDouble(), anyDouble(), any())).thenReturn("Sur");
        clock = new MutableClock(POLL_1);
        scheduler = new OutageDataScheduler(api, repository, neighborhoods, districts, clock,
            new FetchHealthTracker(clock), mock(OutageAnnouncer.class), mock(WeeklySummaryAnnouncer.class));
    }

    @Test
    void poligonoSurCorrectionKeepsOneRowThatIsNeitherBriefNorRestored() {
        // Poll 1: A, start 23:00, 9 clients.
        when(api.fetchSevillaOutages()).thenReturn(List.of(feed("A", "30/09/2026 23:00", 9)));
        poll(POLL_1);

        // Poll 2: only B, start 22:50, same point/type, 9 clients.
        when(api.fetchSevillaOutages()).thenReturn(List.of(feed("B", "30/09/2026 22:50", 9)));
        poll(POLL_2);

        List<EnelOutage> rows = repository.findAll();
        assertThat(rows).hasSize(1);
        EnelOutage row = rows.get(0);
        assertThat(row.getInterruptionDate()).isEqualTo(START_B);
        assertThat(row.getOriginalInterruptionDate()).isEqualTo(START_A);
        assertThat(row.getStartCorrectedAt()).isEqualTo(POLL_2);
        assertThat(row.getFirstSeenAt()).isEqualTo(POLL_1);
        assertThat(row.getFetchedAt()).isEqualTo(POLL_2);
        assertThat(row.getObjectId()).isEqualTo("B");
        assertThat(row.getAffectedClients()).isEqualTo(9);
        assertThat(row.isActive()).isTrue();
        assertThat(row.getResolvedAt()).isNull();
        assertThat(row.isBrief()).isFalse();
        assertThat(row.getMissingPolls()).isZero();

        // Telegram: nothing to announce as restored (the row never went inactive), and the
        // outage is a "new" candidate exactly as a two-poll-confirmed outage should be.
        assertThat(repository.findRestoredOutagesToAnnounce(1)).isEmpty();
        assertThat(repository.findNewOutagesToAnnounce(POLL_2, POLL_2.minusHours(12)))
            .extracting(EnelOutage::getInterruptionDate).containsExactly(START_B);

        // Poll 3: B is gone. The usual lower-bound resolution applies to the single row (after
        // the two-poll mass-resolution guard, since it is the only active outage).
        when(api.fetchSevillaOutages()).thenReturn(List.of());
        poll(POLL_3);
        assertThat(repository.findAll()).singleElement().satisfies(o -> assertThat(o.isActive()).isTrue());
        poll(POLL_3.plusMinutes(5));
        assertThat(repository.findAll()).singleElement().satisfies(o -> {
            assertThat(o.isActive()).isFalse();
            assertThat(o.getResolvedAt()).isEqualTo(POLL_2);
            assertThat(o.isBrief()).isFalse();
            assertThat(o.getInterruptionDate()).isEqualTo(START_B);
        });
    }

    @Test
    void aNewOutageThatStartsAfterTheOldOneWasLastSeenStaysASeparateRow() {
        when(api.fetchSevillaOutages()).thenReturn(List.of(feed("A", "30/09/2026 23:00", 9)));
        poll(POLL_1);

        // Started at 23:07, after A was last published at 23:05:09: a genuine new outage.
        when(api.fetchSevillaOutages()).thenReturn(List.of(feed("C", "30/09/2026 23:07", 4)));
        poll(POLL_2);
        // A is the only row that was active, so its resolution waits for a confirming poll.
        assertThat(repository.findAll()).hasSize(2).allSatisfy(o -> assertThat(o.isActive()).isTrue());
        poll(POLL_3);

        List<EnelOutage> rows = repository.findAllByOrderByInterruptionDateDesc();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getInterruptionDate()).isEqualTo(LocalDateTime.of(2026, 9, 30, 23, 7));
        assertThat(rows.get(0).isActive()).isTrue();
        assertThat(rows.get(1).getInterruptionDate()).isEqualTo(START_A);
        assertThat(rows.get(1).isActive()).isFalse();
        assertThat(rows.get(1).getResolvedAt()).isEqualTo(POLL_1);
        assertThat(rows.get(1).isBrief()).isTrue();
        assertThat(rows).allSatisfy(o -> assertThat(o.getOriginalInterruptionDate()).isNull());
    }

    private void poll(LocalDateTime madridNow) {
        clock.now = madridNow;
        scheduler.fetchAndSaveOutages();
        em.flush();
        em.clear();
    }

    private static EnelApiFeatureWithEvidence feed(String objectId, String interruptionDate, int clients) {
        EnelApiResponse.Feature feature = new EnelApiResponse.Feature();
        EnelApiResponse.Attributes attr = new EnelApiResponse.Attributes();
        attr.setObjectId(objectId);
        attr.setLatitude(LAT);
        attr.setLongitude(LON);
        attr.setInterruptionDate(interruptionDate);
        attr.setServiceType("BT");
        attr.setAffectedClient(clients);
        attr.setCause("Avería");
        feature.setAttributes(attr);
        return new EnelApiFeatureWithEvidence(feature, "http://source", "{\"id\":\"" + objectId + "\"}");
    }

    private static final class MutableClock extends Clock {
        LocalDateTime now;

        MutableClock(LocalDateTime now) {
            this.now = now;
        }

        @Override public ZoneId getZone() { return MADRID; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.atZone(MADRID).toInstant(); }
    }
}
