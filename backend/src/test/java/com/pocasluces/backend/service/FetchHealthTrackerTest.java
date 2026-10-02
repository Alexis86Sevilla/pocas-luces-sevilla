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
        assertThat(tracker.getLastFeatureCount()).isEmpty();
        assertThat(tracker.getConsecutiveEmptyPolls()).isZero();
        assertThat(tracker.getStartedAt()).isEqualTo(Instant.parse("2026-07-10T12:00:00Z"));
    }

    @Test
    void shouldRecordImmediatelyWithoutTransaction() {
        tracker.recordSuccess(7);

        assertThat(tracker.getLastSuccessfulFetch()).contains(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(tracker.getLastFeatureCount()).contains(7);
        assertThat(tracker.getConsecutiveEmptyPolls()).isZero();
    }

    @Test
    void shouldCountConsecutiveEmptyPollsAndResetOnTheFirstNonEmptyOne() {
        tracker.recordSuccess(0);
        tracker.recordSuccess(0);
        assertThat(tracker.getConsecutiveEmptyPolls()).isEqualTo(2);
        assertThat(tracker.getLastFeatureCount()).contains(0);

        tracker.recordSuccess(3);
        assertThat(tracker.getConsecutiveEmptyPolls()).isZero();
        assertThat(tracker.getLastFeatureCount()).contains(3);

        tracker.recordSuccess(0);
        assertThat(tracker.getConsecutiveEmptyPolls()).isEqualTo(1);
    }

    @Test
    void shouldRecordOnlyAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        tracker.recordSuccess(2);
        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();
        assertThat(tracker.getLastFeatureCount()).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(tracker.getLastSuccessfulFetch()).contains(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(tracker.getLastFeatureCount()).contains(2);
    }

    @Test
    void shouldNotRecordWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        tracker.recordSuccess(0);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(tracker.getLastSuccessfulFetch()).isEmpty();
        assertThat(tracker.getConsecutiveEmptyPolls()).isZero();
    }
}
