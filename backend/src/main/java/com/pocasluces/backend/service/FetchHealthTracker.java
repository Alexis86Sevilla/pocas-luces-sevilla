package com.pocasluces.backend.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory record of the last successful Endesa fetch, read by the health endpoint.
 * The state is intentionally not persisted: after a restart the endpoint reports STARTING
 * until the first fetch succeeds.
 */
@Component
public class FetchHealthTracker {

    private final Clock clock;
    private final Instant startedAt;
    private final AtomicReference<Instant> lastSuccessfulFetch = new AtomicReference<>();

    public FetchHealthTracker(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    /**
     * Records a successful fetch. When called inside a transaction the timestamp is only
     * recorded after the commit succeeds, so a failed commit (rollback) is never reported as a
     * success. Without an active transaction it is recorded immediately.
     */
    public void recordSuccess() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recordNow();
                }
            });
        } else {
            recordNow();
        }
    }

    private void recordNow() {
        lastSuccessfulFetch.set(clock.instant());
    }

    public Optional<Instant> getLastSuccessfulFetch() {
        return Optional.ofNullable(lastSuccessfulFetch.get());
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
