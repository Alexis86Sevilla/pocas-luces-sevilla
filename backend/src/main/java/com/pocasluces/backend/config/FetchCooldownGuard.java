package com.pocasluces.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Guards manually triggered fetches (POST /api/outages/fetch) with a cooldown, so a client
 * cannot repeatedly hammer the upstream Enel API. Configured via {@code admin.fetch.cooldown}
 * (defaults to 5 minutes).
 */
@Component
public class FetchCooldownGuard {

    private final Duration cooldown;
    private final Clock clock;
    private final AtomicReference<Instant> lastFetchAt = new AtomicReference<>(Instant.EPOCH);

    public FetchCooldownGuard(@Value("${admin.fetch.cooldown:5m}") Duration cooldown, Clock clock) {
        this.cooldown = cooldown;
        this.clock = clock;
    }

    /**
     * Throws 429 Too Many Requests if the previous accepted fetch happened less than
     * {@code cooldown} ago. Thread-safe: concurrent callers race on a compare-and-set so at
     * most one passes per cooldown window.
     */
    public void requireCooldownElapsed() {
        while (true) {
            Instant previous = lastFetchAt.get();
            Instant now = clock.instant();
            Duration sinceLast = Duration.between(previous, now);
            if (sinceLast.compareTo(cooldown) < 0) {
                long remainingSeconds = cooldown.minus(sinceLast).toSeconds();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Fetch was triggered recently, try again in " + remainingSeconds + "s");
            }
            if (lastFetchAt.compareAndSet(previous, now)) {
                return;
            }
            // Another thread updated lastFetchAt concurrently; retry with the fresh value.
        }
    }
}
