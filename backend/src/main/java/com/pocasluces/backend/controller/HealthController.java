package com.pocasluces.backend.controller;

import com.pocasluces.backend.service.FetchHealthTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Public health endpoint for external uptime monitors. Reports whether the backend is still
 * refreshing Endesa data, not just whether the process is alive. Exposes nothing beyond the
 * status and the last successful fetch time.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    public enum Status { UP, STALE, STARTING }

    public record HealthResponse(Status status, Instant lastSuccessfulFetch, Long ageSeconds) {}

    private final FetchHealthTracker tracker;
    private final Clock clock;
    private final Duration maxFetchAge;
    private final Duration startupGrace;

    public HealthController(FetchHealthTracker tracker,
                            Clock clock,
                            @Value("${health.max-fetch-age:20m}") Duration maxFetchAge,
                            @Value("${health.startup-grace:10m}") Duration startupGrace) {
        this.tracker = tracker;
        this.clock = clock;
        this.maxFetchAge = maxFetchAge;
        this.startupGrace = startupGrace;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        Instant now = clock.instant();
        Instant last = tracker.getLastSuccessfulFetch().orElse(null);

        Status status;
        Long ageSeconds = null;
        if (last != null) {
            Duration age = Duration.between(last, now);
            ageSeconds = Math.max(0, age.toSeconds());
            status = age.compareTo(maxFetchAge) <= 0 ? Status.UP : Status.STALE;
        } else {
            Duration uptime = Duration.between(tracker.getStartedAt(), now);
            status = uptime.compareTo(startupGrace) <= 0 ? Status.STARTING : Status.STALE;
        }

        HttpStatus http = status == Status.STALE ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(http)
            .cacheControl(CacheControl.noStore())
            .body(new HealthResponse(status, last, ageSeconds));
    }
}
