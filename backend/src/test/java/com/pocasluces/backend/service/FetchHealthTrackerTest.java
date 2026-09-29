package com.pocasluces.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FetchHealthTrackerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneId.of("UTC"));
    private final FetchHealthTracker tracker = new FetchHealthTracker(clock);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldStartWithoutSuccessfulFetchAndKeepStartInstant() {
        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();
        assertThat(tracker.getStartedAt()).isEqualTo(Instant.parse("2026-07-10T12:00:00Z"));
    }

    @Test
    void shouldRecordImmediatelyWithoutTransaction() {
        tracker.recordSuccess();

        assertThat(tracker.getLastSuccessfulFetch()).contains(Instant.parse("2026-07-10T12:00:00Z"));
    }

    @Test
    void shouldRecordOnlyAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        tracker.recordSuccess();
        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(tracker.getLastSuccessfulFetch()).contains(Instant.parse("2026-07-10T12:00:00Z"));
    }

    @Test
    void shouldNotRecordWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        tracker.recordSuccess();
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();
    }
}
