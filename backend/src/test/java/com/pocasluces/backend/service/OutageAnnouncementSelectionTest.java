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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the real scheduler and repository through a sequence of polls (H2) and checks
 * which outages the announcement queries select after each one. This is the persisted
 * two-poll confirmation logic {@link OutageAnnouncer} relies on, both for "new" and for
 * "restored", independent of Telegram itself (the announcer is a mock here; marking is
 * done directly through the repository, exactly as the announcer does).
 *
 * <p>Times are Europe/Madrid wall-clock through a fixed Madrid {@link Clock} per poll, so
 * the JVM default zone is irrelevant (the suite runs under UTC and Europe/Madrid).</p>
 */
@DataJpaTest
@ActiveProfiles("dev")
@Import(EnelOutageRepositoryImpl.class)
class OutageAnnouncementSelectionTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter FEED_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 29, 11, 0);
    private static final int MIN_MISSING = OutageAnnouncer.MIN_MISSING_POLLS_FOR_RESTORATION;

    @Autowired
    private EnelOutageRepository repository;

    @Autowired
    private TestEntityManager em;

    private EnelApiService api;
    private NeighborhoodLocator neighborhoods;
    private DistrictLocator districts;
    private MutableClock clock;
    /**
     * One scheduler for the whole scenario, as in production: the mass-resolution guard keeps
     * its "deferred last poll" state in memory, so a poll that would resolve every active
     * outage (here: the only one) is applied only when the next poll says the same.
     */
    private OutageDataScheduler scheduler;

    @BeforeEach
    void setUp() {
        api = mock(EnelApiService.class);
        neighborhoods = mock(NeighborhoodLocator.class);
        districts = mock(DistrictLocator.class);
        when(neighborhoods.findNeighborhood(anyDouble(), anyDouble())).thenReturn("León");
        when(districts.findDistrict(anyDouble(), anyDouble(), any())).thenReturn("Triana");
        clock = new MutableClock(T0);
        scheduler = new OutageDataScheduler(api, repository, neighborhoods, districts, clock,
            new FetchHealthTracker(clock), mock(OutageAnnouncer.class), mock(WeeklySummaryAnnouncer.class));
    }

    // ---- new outages ---------------------------------------------------------------------

    @Test
    void newOutageIsSelectedOnlyAfterItsSecondSuccessfulPoll() {
        Feature a = feature("A", 37.38, -5.99, T0.minusMinutes(1));

        poll(T0, a);
        assertThat(newCandidates(T0)).isEmpty();

        poll(at(1), a);
        assertThat(newCandidates(at(1))).extracting(EnelOutage::getObjectId).containsExactly("A");
    }

    @Test
    void newOutageIsNotSelectedAgainOnceMarkedAnnounced() {
        Feature a = feature("A", 37.38, -5.99, T0.minusMinutes(1));
        poll(T0, a);
        poll(at(1), a);

        markAnnounced("A", at(1));

        poll(at(2), a);
        assertThat(newCandidates(at(2))).isEmpty();
    }

    @Test
    void failedPollDoesNotCountAsASighting() {
        Feature a = feature("A", 37.38, -5.99, T0.minusMinutes(1));
        poll(T0, a);

        failedPoll(at(1));
        assertThat(newCandidates(at(1))).isEmpty();

        poll(at(2), a);
        assertThat(newCandidates(at(2))).extracting(EnelOutage::getObjectId).containsExactly("A");
    }

    @Test
    void twoUpsertsOfTheSameOutageWithinOnePollAreOneSighting() {
        // Endesa republishes the same physical outage with a new objectId on another page.
        Feature first = feature("100", 37.38, -5.99, T0.minusMinutes(1));
        Feature second = feature("200", 37.38, -5.99, T0.minusMinutes(1));

        poll(T0, first, second);

        assertThat(newCandidates(T0)).isEmpty();
    }

    @Test
    void staleBacklogIsNeverSelectedAsNew() {
        Feature old = feature("OLD", 37.38, -5.99, T0.minus(OutageAnnouncer.NEW_OUTAGE_MAX_AGE).minusMinutes(10));
        poll(T0, old);
        poll(at(1), old);

        assertThat(newCandidates(at(1))).isEmpty();
    }

    @Test
    void outageWhosePublishedStartIsStillInTheFutureWaitsUntilItStarts() {
        LocalDateTime start = at(1).plusHours(1);
        Feature scheduled = feature("S", 37.38, -5.99, start);
        poll(T0, scheduled);
        poll(at(1), scheduled);
        assertThat(newCandidates(at(1))).isEmpty();

        LocalDateTime later = start.plusMinutes(1);
        poll(later, scheduled);
        assertThat(newCandidates(later)).extracting(EnelOutage::getObjectId).containsExactly("S");
    }

    @Test
    void rowsThatPredateTheFeatureAreNeverSelectedNewNorRestored() {
        // Go-live situation: an active outage backfilled by V6 as announce_eligible = false.
        EnelOutage legacy = EnelOutage.builder()
            .objectId("LEGACY").latitude(37.40).longitude(-5.98).serviceType("AT")
            .interruptionDate(T0.minusHours(1)).neighborhoodName("León").districtName("Triana")
            .firstSeenAt(T0.minusMinutes(30)).fetchedAt(T0.minusMinutes(5))
            .createdAt(T0.minusMinutes(30)).updatedAt(T0.minusMinutes(5))
            .announceEligible(false)
            .build();
        em.persistAndFlush(legacy);
        em.clear();
        Feature stillPublished = feature("LEGACY", 37.40, -5.98, T0.minusHours(1));

        poll(T0, stillPublished);
        poll(at(1), stillPublished);
        assertThat(newCandidates(at(1))).isEmpty();
        assertThat(find("LEGACY").isAnnounceEligible()).as("upsert must not flip eligibility").isFalse();

        poll(at(2));
        poll(at(3));
        assertThat(restoredCandidates()).isEmpty();
        assertThat(find("LEGACY").getMissingPolls()).isZero();
    }

    // ---- restored outages ----------------------------------------------------------------

    @Test
    void restorationIsSelectedOnlyAfterTwoConsecutiveMissingPolls() {
        announcedOutage("A");

        poll(at(2));                    // A is the only active outage: its resolution waits a poll
        assertThat(find("A").isActive()).isTrue();
        assertThat(find("A").getMissingPolls()).isZero();
        assertThat(restoredCandidates()).isEmpty();

        poll(at(3));                    // confirmed: resolved, missing once
        assertThat(find("A").isActive()).isFalse();
        assertThat(find("A").getMissingPolls()).isEqualTo(1);
        assertThat(restoredCandidates()).isEmpty();

        poll(at(4));
        assertThat(find("A").getMissingPolls()).isEqualTo(2);
        assertThat(restoredCandidates()).extracting(EnelOutage::getObjectId).containsExactly("A");
        assertThat(restoredCandidates().get(0).getResolvedAt()).as("last seen, not the deferred flip").isEqualTo(at(1));
    }

    @Test
    void singleMissingPollFollowedByReappearanceProducesNoRestoration() {
        Feature a = announcedOutage("A");

        poll(at(2));                    // partial feed: A missing once (resolution deferred)
        poll(at(3));                    // still missing: resolved now
        assertThat(find("A").isActive()).isFalse();
        assertThat(restoredCandidates()).isEmpty();

        poll(at(4), a);                 // recovery: A is back
        assertThat(find("A").isActive()).isTrue();
        assertThat(find("A").getResolvedAt()).isNull();
        assertThat(find("A").getMissingPolls()).isZero();
        assertThat(restoredCandidates()).isEmpty();

        poll(at(5));                    // counting starts again from zero (and the guard too)
        poll(at(6));
        assertThat(restoredCandidates()).isEmpty();
        poll(at(7));
        assertThat(restoredCandidates()).extracting(EnelOutage::getObjectId).containsExactly("A");
    }

    @Test
    void emptyPollWhileSeveralOutagesAreActiveThenRecoveryProducesNoRestoration() {
        Feature a = feature("A", 37.38, -5.99, T0.minusMinutes(1));
        Feature b = feature("B", 37.39, -5.98, T0.minusMinutes(2));
        Feature c = feature("C", 37.40, -5.97, T0.minusMinutes(3));
        poll(T0, a, b, c);
        poll(at(1), a, b, c);
        assertThat(newCandidates(at(1))).hasSize(3);
        markAnnounced(List.of("A", "B", "C"), at(1));

        poll(at(2));                    // feed answered with zero outages: deferred, nothing changes
        assertThat(repository.findAll()).allSatisfy(o -> {
            assertThat(o.isActive()).isTrue();
            assertThat(o.getMissingPolls()).isZero();
        });
        assertThat(restoredCandidates()).isEmpty();

        poll(at(3), a, b, c);           // everything is back: no message, no trace
        assertThat(restoredCandidates()).isEmpty();
        assertThat(repository.findAll()).allSatisfy(o -> {
            assertThat(o.isActive()).isTrue();
            assertThat(o.getMissingPolls()).isZero();
        });

        poll(at(4));                    // two empty polls in a row: now it is applied
        poll(at(5));
        assertThat(repository.findAll()).allSatisfy(o -> {
            assertThat(o.isActive()).isFalse();
            assertThat(o.getResolvedAt()).isEqualTo(at(3));
            assertThat(o.getMissingPolls()).isEqualTo(1);
        });
        assertThat(restoredCandidates()).isEmpty();

        poll(at(6), a, b, c);           // back again before the second missing poll
        assertThat(restoredCandidates()).isEmpty();
        assertThat(repository.findAll()).allSatisfy(o -> {
            assertThat(o.isActive()).isTrue();
            assertThat(o.getMissingPolls()).isZero();
        });
    }

    @Test
    void failedPollNeitherCountsAsMissingNorResetsTheCounter() {
        announcedOutage("A");
        poll(at(2));
        poll(at(3));
        assertThat(find("A").getMissingPolls()).isEqualTo(1);

        failedPoll(at(4));
        assertThat(find("A").getMissingPolls()).isEqualTo(1);
        assertThat(restoredCandidates()).isEmpty();

        poll(at(5));
        assertThat(restoredCandidates()).extracting(EnelOutage::getObjectId).containsExactly("A");
    }

    @Test
    void restorationIsNeverSelectedForOutagesThatWereNotAnnounced() {
        Feature a = feature("A", 37.38, -5.99, T0.minusMinutes(1));
        poll(T0, a);
        poll(at(1), a);                 // selectable as new, but never marked (e.g. Telegram down)

        poll(at(2));
        poll(at(3));

        assertThat(restoredCandidates()).isEmpty();
        assertThat(find("A").getMissingPolls()).as("counter only tracks announced outages").isZero();
    }

    @Test
    void restorationIsAnnouncedAtMostOnceEvenIfTheOutageFlapsAfterwards() {
        Feature a = announcedOutage("A");
        poll(at(2));                    // deferred (only active outage)
        poll(at(3));                    // resolved, missing once
        poll(at(4));                    // missing twice
        assertThat(restoredCandidates()).hasSize(1);
        Long id = find("A").getId();
        repository.markRestorationAnnounced(List.of(id), at(4));
        flushAndClear();
        assertThat(restoredCandidates()).isEmpty();

        poll(at(5), a);                 // reappears
        assertThat(newCandidates(at(5))).as("already announced as new").isEmpty();
        poll(at(6));
        poll(at(7));
        poll(at(8));
        assertThat(find("A").isActive()).isFalse();
        assertThat(restoredCandidates()).as("already announced as restored").isEmpty();
    }

    @Test
    void markingIsIdempotentAndOnlyAffectsTheGivenIds() {
        Feature a = feature("A", 37.38, -5.99, T0.minusMinutes(1));
        Feature b = feature("B", 37.39, -5.98, T0.minusMinutes(2));
        poll(T0, a, b);
        poll(at(1), a, b);

        int marked = repository.markAnnounced(List.of(find("A").getId()), at(1));
        flushAndClear();
        int markedAgain = repository.markAnnounced(List.of(find("A").getId()), at(2));
        flushAndClear();

        assertThat(marked).isEqualTo(1);
        assertThat(markedAgain).as("an existing mark is never overwritten").isZero();
        assertThat(find("A").getAnnouncedAt()).isEqualTo(at(1));
        assertThat(find("B").getAnnouncedAt()).isNull();
        assertThat(newCandidates(at(2))).extracting(EnelOutage::getObjectId).containsExactly("B");
    }

    // ---- helpers -------------------------------------------------------------------------

    private record Feature(EnelApiFeatureWithEvidence evidence) {}

    private static LocalDateTime at(int pollIndex) {
        return T0.plusMinutes(5L * pollIndex);
    }

    /** Seen at T0 and at(1), then marked as announced at at(1). */
    private Feature announcedOutage(String objectId) {
        Feature f = feature(objectId, 37.38, -5.99, T0.minusMinutes(1));
        poll(T0, f);
        poll(at(1), f);
        assertThat(newCandidates(at(1))).extracting(EnelOutage::getObjectId).containsExactly(objectId);
        markAnnounced(objectId, at(1));
        return f;
    }

    private void poll(LocalDateTime madridNow, Feature... published) {
        List<EnelApiFeatureWithEvidence> feed = java.util.Arrays.stream(published).map(Feature::evidence).toList();
        // doReturn/doThrow: when(api.fetch...()) would invoke the stub, which may currently throw.
        doReturn(feed).when(api).fetchSevillaOutages();
        clock.now = madridNow;
        scheduler.fetchAndSaveOutages();
        flushAndClear();
    }

    private void failedPoll(LocalDateTime madridNow) {
        doThrow(new EnelApiService.EnelApiException("feed down")).when(api).fetchSevillaOutages();
        clock.now = madridNow;
        scheduler.fetchAndSaveOutages();
        flushAndClear();
    }

    /** Europe/Madrid clock whose wall-clock "now" each poll sets. */
    private static final class MutableClock extends Clock {
        LocalDateTime now;

        MutableClock(LocalDateTime now) {
            this.now = now;
        }

        @Override public ZoneId getZone() { return MADRID; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return now.atZone(MADRID).toInstant(); }
    }

    private List<EnelOutage> newCandidates(LocalDateTime now) {
        return repository.findNewOutagesToAnnounce(now, now.minus(OutageAnnouncer.NEW_OUTAGE_MAX_AGE));
    }

    private List<EnelOutage> restoredCandidates() {
        return repository.findRestoredOutagesToAnnounce(MIN_MISSING);
    }

    private void markAnnounced(String objectId, LocalDateTime at) {
        markAnnounced(List.of(objectId), at);
    }

    private void markAnnounced(List<String> objectIds, LocalDateTime at) {
        List<Long> ids = objectIds.stream().map(id -> find(id).getId()).toList();
        repository.markAnnounced(ids, at);
        flushAndClear();
    }

    private EnelOutage find(String objectId) {
        return repository.findByObjectId(objectId).orElseThrow();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static Feature feature(String objectId, double lat, double lon, LocalDateTime interruption) {
        EnelApiResponse.Attributes attr = new EnelApiResponse.Attributes();
        attr.setObjectId(objectId);
        attr.setLatitude(lat);
        attr.setLongitude(lon);
        attr.setAffectedClient(120);
        attr.setInterruptionDate(interruption.format(FEED_DATE));
        attr.setServiceType("AT");
        attr.setCause("Avería");
        EnelApiResponse.Feature feature = new EnelApiResponse.Feature();
        feature.setAttributes(attr);
        return new Feature(new EnelApiFeatureWithEvidence(feature, "https://feed/query", "{\"id\":\"" + objectId + "\"}"));
    }
}
