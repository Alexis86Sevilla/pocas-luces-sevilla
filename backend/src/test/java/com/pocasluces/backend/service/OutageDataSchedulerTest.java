package com.pocasluces.backend.service;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import com.pocasluces.backend.dto.EnelApiFeatureWithEvidence;
import com.pocasluces.backend.dto.EnelApiResponse;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutageDataSchedulerTest {

    @Mock
    private EnelApiService enelApiService;

    @Mock
    private EnelOutageRepository repository;

    @Mock
    private NeighborhoodLocator locator;

    @Mock
    private DistrictLocator districtLocator;

    @Mock
    private OutageAnnouncer announcer;

    @Mock
    private WeeklySummaryAnnouncer weeklySummaryAnnouncer;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneId.of("UTC"));
    private FetchHealthTracker tracker;
    private OutageDataScheduler scheduler;

    @BeforeEach
    void setUp() {
        tracker = new FetchHealthTracker(clock);
        scheduler = new OutageDataScheduler(enelApiService, repository, locator, districtLocator, clock, tracker, announcer, weeklySummaryAnnouncer);
    }

    @Test
    void shouldCountMissingPollsAfterResolvingAndThenAskTheAnnouncerAfterCommit() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());

        scheduler.fetchAndSaveOutages();

        InOrder inOrder = inOrder(repository, announcer, weeklySummaryAnnouncer);
        inOrder.verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));
        inOrder.verify(repository).incrementMissingPollsOfAnnouncedInactiveOutages();
        inOrder.verify(announcer).announceAfterCommit();
        inOrder.verify(weeklySummaryAnnouncer).announceAfterCommit(any());
    }

    @Test
    void shouldNeitherCountMissingPollsNorAnnounceWhenFetchFails() {
        when(enelApiService.fetchSevillaOutages()).thenThrow(new EnelApiService.EnelApiException("API down"));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).incrementMissingPollsOfAnnouncedInactiveOutages();
        verify(announcer, never()).announceAfterCommit();
        verify(weeklySummaryAnnouncer, never()).announceAfterCommit(any());
    }

    @Test
    void shouldInsertNewOutageAndSetFirstSeenAt() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{\"raw\":\"data\"}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        EnelOutage saved = captor.getValue();

        assertThat(saved.getObjectId()).isEqualTo("123");
        assertThat(saved.getNeighborhoodName()).isEqualTo("San Pablo");
        assertThat(saved.getDistrictName()).isEqualTo("San Pablo-Santa Justa");
        assertThat(saved.getServiceType()).isEqualTo("AT");
        assertThat(saved.getInterruptionDate()).isEqualTo(LocalDateTime.of(2026, 7, 10, 8, 30));
        assertThat(saved.getFetchedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(saved.getFirstSeenAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(saved.getRawResponse()).isEqualTo("{\"raw\":\"data\"}");
        assertThat(saved.getRawResponseHash()).hasSize(64);
        assertThat(saved.getSourceUrl()).isEqualTo("http://source");
    }

    @Test
    void shouldPassCurrentTimestampsToAtomicUpsert() {
        EnelApiResponse.Feature feature = feature("456", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{\"raw\":\"data\"}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        EnelOutage upserted = captor.getValue();

        assertThat(upserted.getObjectId()).isEqualTo("456");
        assertThat(upserted.getFirstSeenAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(upserted.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(upserted.getUpdatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(upserted.getFetchedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void shouldSkipOutageWithNullObjectId() {
        EnelApiResponse.Feature feature = feature(null, "10/07/2026 08:30", 37.0, -5.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldSkipOutageWithUnparseableInterruptionDate() {
        EnelApiResponse.Feature feature = feature("123", "not-a-date", 37.0, -5.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldAbortWhenApiThrows() {
        when(enelApiService.fetchSevillaOutages())
            .thenThrow(new EnelApiService.EnelApiException("API down"));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).upsert(any());
        verify(repository, never()).resolveStaleActiveOutages(any());
    }

    @Test
    void shouldUpsertFetchedOutagesActiveAndUnresolvedBeforeResolvingStaleOnes() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).upsert(any());
        inOrder.verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getResolvedAt()).isNull();
    }

    @Test
    void shouldResolveStaleActiveOutagesWhenFetchReturnsNoOutages() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.resolveStaleActiveOutages(LocalDateTime.now(clock))).thenReturn(2);

        scheduler.fetchAndSaveOutages();

        verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));
        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldNotWarnWhenFetchReturnsNoOutagesAndNoneWereActive() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.resolveStaleActiveOutages(LocalDateTime.now(clock))).thenReturn(0);

        scheduler.fetchAndSaveOutages();

        verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));
        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldTolerateObjectIdChangeForSameNaturalKey() {
        EnelApiResponse.Feature first = feature("100", "10/07/2026 08:30", 37.3970, -5.9800, "AT");
        EnelApiResponse.Feature second = feature("200", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(
                new EnelApiFeatureWithEvidence(first, "http://page1", "{\"id\":100}"),
                new EnelApiFeatureWithEvidence(second, "http://page2", "{\"id\":200}")
            ));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        // Two upserts should be issued for the same natural key; the repository upsert
        // is atomic and the second call simply updates the row to objectId 200.
        verify(repository, times(2)).upsert(any());
    }

    @Test
    void shouldFallbackToUnknownDistrictWhenLocatorReturnsNull() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn(null);

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().getDistrictName()).isEqualTo("Zona no identificada");
    }

    @Test
    void shouldPersistCauseFromEndesaFeed() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");
        feature.getAttributes().setCause("Avería");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().getCause()).isEqualTo("Avería");
    }

    @Test
    void shouldPersistDefaultedZeroCoordinatesInsteadOfNull() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", null, null, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().getLatitude()).isEqualTo(0.0);
        assertThat(captor.getValue().getLongitude()).isEqualTo(0.0);
    }

    @Test
    void shouldFallbackToUnknownDistrictForZeroCoordinates() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 0.0, 0.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().getDistrictName()).isEqualTo("Zona no identificada");
    }

    private EnelApiResponse.Feature feature(String objectId, String interruptionDate,
                                            Double lat, Double lon, String serviceType) {
        EnelApiResponse.Feature feature = new EnelApiResponse.Feature();
        EnelApiResponse.Attributes attr = new EnelApiResponse.Attributes();
        attr.setObjectId(objectId);
        attr.setLatitude(lat);
        attr.setLongitude(lon);
        attr.setInterruptionDate(interruptionDate);
        attr.setServiceType(serviceType);
        feature.setAttributes(attr);
        return feature;
    }

    @Test
    void shouldNotRecordHealthWhenFetchFails() {
        when(enelApiService.fetchSevillaOutages()).thenThrow(new EnelApiService.EnelApiException("boom"));

        scheduler.fetchAndSaveOutages();

        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();
    }

    @Test
    void shouldRecordHealthOnSuccessfulFetchWithZeroFeatures() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());

        scheduler.fetchAndSaveOutages();

        assertThat(tracker.getLastSuccessfulFetch()).contains(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(tracker.getLastFeatureCount()).contains(0);
        assertThat(tracker.getConsecutiveEmptyPolls()).isEqualTo(1);
    }

    @Test
    void shouldRecordTheFeatureCountOfASuccessfulFetch() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");
        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        assertThat(tracker.getLastFeatureCount()).contains(1);
        assertThat(tracker.getConsecutiveEmptyPolls()).isZero();
    }

    // ---- Feed guard: skipped-feature ratio -------------------------------------------------

    @Test
    void shouldNotApplyAPollWhenMoreThanTwentyPercentOfItsFeaturesCannotBeParsed() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of(
            evidence(feature("1", "10/07/2026 08:30", 37.0, -5.0, "AT")),
            evidence(feature("2", "10/07/2026 08:31", 37.0, -5.0, "AT")),
            evidence(feature("3", "10/07/2026 08:32", 37.0, -5.0, "AT")),
            evidence(feature("4", "not-a-date", 37.0, -5.0, "AT")),
            evidence(feature(null, "10/07/2026 08:34", 37.0, -5.0, "AT"))));

        scheduler.fetchAndSaveOutages();

        // 2 of 5 skipped (40%): nothing written, nothing resolved, not a successful poll.
        verify(repository, never()).upsert(any());
        verify(repository, never()).resolveStaleActiveOutages(any());
        verify(repository, never()).incrementMissingPollsOfAnnouncedInactiveOutages();
        verify(announcer, never()).announceAfterCommit();
        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();
    }

    @Test
    void shouldApplyAPollWhoseSkippedShareIsWithinTheLimit() {
        List<EnelApiFeatureWithEvidence> features = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            features.add(evidence(feature(String.valueOf(i), "10/07/2026 08:30", 37.0 + i, -5.0, "AT")));
        }
        features.add(evidence(feature("10", "not-a-date", 37.0, -5.0, "AT")));
        when(enelApiService.fetchSevillaOutages()).thenReturn(features);

        scheduler.fetchAndSaveOutages();

        // 1 of 10 skipped (10%): the 9 good rows are saved and the poll counts as successful.
        verify(repository, times(9)).upsert(any());
        verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));
        assertThat(tracker.getLastSuccessfulFetch()).isPresent();
    }

    // ---- Feed guard: mass resolution needs two consecutive polls -----------------------------

    @Test
    void shouldDeferResolvingEveryActiveOutageUntilTheNextPollConfirmsIt() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(3L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(3L);

        scheduler.fetchAndSaveOutages();
        verify(repository, never()).resolveStaleActiveOutages(any());
        // The poll itself succeeded: the feed was read, only the resolution waits.
        assertThat(tracker.getLastSuccessfulFetch()).isPresent();

        scheduler.fetchAndSaveOutages();
        verify(repository, times(1)).resolveStaleActiveOutages(LocalDateTime.now(clock));
    }

    @Test
    void shouldDeferWhenMoreThanHalfOfAtLeastFourActiveOutagesWouldResolve() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(4L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(3L);

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).resolveStaleActiveOutages(any());
    }

    @Test
    void shouldResolveImmediatelyWhenHalfOrFewerWouldResolve() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(10L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(5L);

        scheduler.fetchAndSaveOutages();

        verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));
    }

    @Test
    void shouldResolveImmediatelyWhenSomeButNotAllOfFewerThanFourActiveOutagesVanish() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(3L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(2L);

        scheduler.fetchAndSaveOutages();

        verify(repository).resolveStaleActiveOutages(LocalDateTime.now(clock));
    }

    @Test
    void shouldNotKeepADeferralWhoseRunRolledBack() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(3L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(3L);

        // First poll defers inside a transaction that then rolls back.
        TransactionSynchronizationManager.initSynchronization();
        try {
            scheduler.fetchAndSaveOutages();
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // The deferral was never committed, so the next poll must defer again, not resolve.
        scheduler.fetchAndSaveOutages();
        verify(repository, never()).resolveStaleActiveOutages(any());
    }

    @Test
    void shouldKeepTheDeferralOnceTheRunCommits() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(3L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(3L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            scheduler.fetchAndSaveOutages();
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        scheduler.fetchAndSaveOutages();
        verify(repository, times(1)).resolveStaleActiveOutages(LocalDateTime.now(clock));
    }

    @Test
    void shouldRequireTwoConsecutiveSuccessfulPollsForAMassResolution() {
        when(repository.countByActiveTrue()).thenReturn(3L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(3L);

        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        scheduler.fetchAndSaveOutages();                       // deferred
        when(enelApiService.fetchSevillaOutages()).thenThrow(new EnelApiService.EnelApiException("down"));
        scheduler.fetchAndSaveOutages();                       // failed poll breaks the sequence
        doReturn(List.of()).when(enelApiService).fetchSevillaOutages();
        scheduler.fetchAndSaveOutages();                       // deferred again

        verify(repository, never()).resolveStaleActiveOutages(any());
    }

    @Test
    void shouldForgetADeferredMassResolutionOnceANormalPollHappens() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());
        when(repository.countByActiveTrue()).thenReturn(3L);
        when(repository.countActiveNotFetchedAt(LocalDateTime.now(clock))).thenReturn(3L, 0L, 3L);

        scheduler.fetchAndSaveOutages();                       // deferred
        scheduler.fetchAndSaveOutages();                       // nothing to resolve: resets
        scheduler.fetchAndSaveOutages();                       // deferred again, not applied

        verify(repository, times(1)).resolveStaleActiveOutages(LocalDateTime.now(clock)); // the middle, harmless run
    }

    // ---- Start-time corrections ----------------------------------------------------------------

    @Test
    void shouldMergeAStartTimeCorrectionIntoTheExistingRowInsteadOfInsertingASecondOne() {
        // Polígono Sur, 2026-09-30: A (start 23:00, 9 clients) was published once, at 23:05:09.
        // The 23:10:09 poll no longer carries A but publishes B: same point and type, 9 clients,
        // start corrected to 22:50. One physical outage, one row.
        LocalDateTime previousPoll = LocalDateTime.of(2026, 9, 30, 23, 5, 9);
        LocalDateTime thisPoll = LocalDateTime.of(2026, 9, 30, 23, 10, 9);
        OutageDataScheduler scheduler = schedulerAt(thisPoll);
        EnelOutage existing = activeRow(41L, 37.3521, -5.9712, "BT", LocalDateTime.of(2026, 9, 30, 23, 0), previousPoll);
        when(repository.findAllActive()).thenReturn(List.of(existing));
        EnelApiResponse.Feature corrected = feature("B", "30/09/2026 22:50", 37.3521, -5.9712, "BT");
        corrected.getAttributes().setAffectedClient(9);
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of(evidence(corrected)));
        when(repository.correctInterruptionDate(41L, LocalDateTime.of(2026, 9, 30, 22, 50), thisPoll)).thenReturn(1);

        scheduler.fetchAndSaveOutages();

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).correctInterruptionDate(41L, LocalDateTime.of(2026, 9, 30, 22, 50), thisPoll);
        inOrder.verify(repository).upsert(argThat(o -> o.getInterruptionDate().equals(LocalDateTime.of(2026, 9, 30, 22, 50))
            && o.getAffectedClients() == 9 && o.getLatitude() == 37.3521 && o.getServiceType().equals("BT")));
        inOrder.verify(repository).resolveStaleActiveOutages(thisPoll);
        verify(repository, times(1)).upsert(any());
    }

    @Test
    void shouldNotCorrectWhenTheNewStartIsAfterTheVanishedRowWasLastSeen() {
        LocalDateTime previousPoll = LocalDateTime.of(2026, 9, 30, 23, 5, 9);
        OutageDataScheduler scheduler = schedulerAt(LocalDateTime.of(2026, 9, 30, 23, 10, 9));
        when(repository.findAllActive()).thenReturn(List.of(
            activeRow(41L, 37.3521, -5.9712, "BT", LocalDateTime.of(2026, 9, 30, 23, 0), previousPoll)));
        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(evidence(feature("B", "30/09/2026 23:07", 37.3521, -5.9712, "BT"))));

        scheduler.fetchAndSaveOutages();

        // Started after A's last sighting: a genuine new outage at the same point.
        verify(repository, never()).correctInterruptionDate(anyLong(), any(), any());
        verify(repository).upsert(any());
    }

    @Test
    void shouldNotCorrectWhenTheMatchIsAmbiguous() {
        LocalDateTime previousPoll = LocalDateTime.of(2026, 9, 30, 23, 5, 9);
        OutageDataScheduler scheduler = schedulerAt(LocalDateTime.of(2026, 9, 30, 23, 10, 9));
        when(repository.findAllActive()).thenReturn(List.of(
            activeRow(41L, 37.3521, -5.9712, "BT", LocalDateTime.of(2026, 9, 30, 23, 0), previousPoll),
            activeRow(42L, 37.3521, -5.9712, "BT", LocalDateTime.of(2026, 9, 30, 23, 1), previousPoll)));
        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(evidence(feature("B", "30/09/2026 22:50", 37.3521, -5.9712, "BT"))));

        scheduler.fetchAndSaveOutages();

        // Two rows vanished and one appeared: which one was corrected? Unknown, so neither.
        verify(repository, never()).correctInterruptionDate(anyLong(), any(), any());
        verify(repository).upsert(any());
    }

    @Test
    void shouldNotCorrectWhenAnotherRowAlreadyOwnsTheCorrectedKey() {
        LocalDateTime previousPoll = LocalDateTime.of(2026, 9, 30, 23, 5, 9);
        OutageDataScheduler scheduler = schedulerAt(LocalDateTime.of(2026, 9, 30, 23, 10, 9));
        when(repository.findAllActive()).thenReturn(List.of(
            activeRow(41L, 37.3521, -5.9712, "BT", LocalDateTime.of(2026, 9, 30, 23, 0), previousPoll)));
        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(evidence(feature("B", "30/09/2026 22:50", 37.3521, -5.9712, "BT"))));
        when(repository.findByLatitudeAndLongitudeAndInterruptionDateAndServiceType(
            37.3521, -5.9712, LocalDateTime.of(2026, 9, 30, 22, 50), "BT"))
            .thenReturn(Optional.of(EnelOutage.builder().id(7L).build()));

        scheduler.fetchAndSaveOutages();

        // An older, resolved row has that key: the upsert re-opens it; moving row 41 onto the
        // same key would violate the unique constraint.
        verify(repository, never()).correctInterruptionDate(anyLong(), any(), any());
        verify(repository).upsert(any());
    }

    @Test
    void shouldNotCorrectWhenTheExistingRowIsStillPublished() {
        LocalDateTime previousPoll = LocalDateTime.of(2026, 9, 30, 23, 5, 9);
        OutageDataScheduler scheduler = schedulerAt(LocalDateTime.of(2026, 9, 30, 23, 10, 9));
        when(repository.findAllActive()).thenReturn(List.of(
            activeRow(41L, 37.3521, -5.9712, "BT", LocalDateTime.of(2026, 9, 30, 23, 0), previousPoll)));
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of(
            evidence(feature("A", "30/09/2026 23:00", 37.3521, -5.9712, "BT")),
            evidence(feature("B", "30/09/2026 22:50", 37.3521, -5.9712, "BT"))));

        scheduler.fetchAndSaveOutages();

        // Both are in the feed: two outages at one point, nothing vanished.
        verify(repository, never()).correctInterruptionDate(anyLong(), any(), any());
        verify(repository, times(2)).upsert(any());
    }

    private OutageDataScheduler schedulerAt(LocalDateTime madridNow) {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        Clock at = Clock.fixed(madridNow.atZone(madrid).toInstant(), madrid);
        return new OutageDataScheduler(enelApiService, repository, locator, districtLocator, at,
            new FetchHealthTracker(at), announcer, weeklySummaryAnnouncer);
    }

    private static EnelOutage activeRow(long id, double lat, double lon, String serviceType,
                                        LocalDateTime start, LocalDateTime lastSeen) {
        return EnelOutage.builder()
            .id(id).latitude(lat).longitude(lon).serviceType(serviceType).interruptionDate(start)
            .firstSeenAt(lastSeen).fetchedAt(lastSeen).createdAt(lastSeen).updatedAt(lastSeen)
            .neighborhoodName("Polígono Sur").affectedClients(9).active(true)
            .build();
    }

    private static EnelApiFeatureWithEvidence evidence(EnelApiResponse.Feature feature) {
        return new EnelApiFeatureWithEvidence(feature, "http://source", "{}");
    }
}
