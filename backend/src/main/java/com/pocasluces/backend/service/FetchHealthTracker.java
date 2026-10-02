package com.pocasluces.backend.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory record of the last successful Endesa fetch, read by the health endpoint.
 * The state is intentionally not persisted: after a restart the endpoint reports STARTING
 * until the first fetch succeeds.
 *
 * <p>Besides the timestamp it keeps how many features the last successful poll carried and
 * how many successful polls in a row carried none, so an external monitor can tell an
 * outage-free Sevilla from a feed that went quiet (both are "successful" polls).</p>
 */
@Component
public class FetchHealthTracker {

    private final Clock clock;
    private final Instant startedAt;
    private final AtomicReference<Instant> lastSuccessfulFetch = new AtomicReference<>();
    private final AtomicReference<Integer> lastFeatureCount = new AtomicReference<>();
    private final AtomicInteger consecutiveEmptyPolls = new AtomicInteger();

    public FetchHealthTracker(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    /**
     * Records a successful fetch that processed {@code featureCount} features. When called
     * inside a transaction the record is only made after the commit succeeds, so a failed
     * commit (rollback) is never reported as a success. Without an active transaction it is
     * recorded immediately. A run that fails a feed guard must not call this at all.
     */
    public void recordSuccess(int featureCount) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recordNow(featureCount);
                }
            });
        } else {
            recordNow(featureCount);
        }
    }

    private void recordNow(int featureCount) {
        lastSuccessfulFetch.set(clock.instant());
        lastFeatureCount.set(featureCount);
        if (featureCount == 0) {
            consecutiveEmptyPolls.incrementAndGet();
        } else {
            consecutiveEmptyPolls.set(0);
        }
    }

    public Optional<Instant> getLastSuccessfulFetch() {
        return Optional.ofNullable(lastSuccessfulFetch.get());
    }

    /** Features in the last successful poll; empty until the first success. */
    public Optional<Integer> getLastFeatureCount() {
        return Optional.ofNullable(lastFeatureCount.get());
    }

    /** Successful polls in a row that carried zero features; 0 after any non-empty success. */
    public int getConsecutiveEmptyPolls() {
        return consecutiveEmptyPolls.get();
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
