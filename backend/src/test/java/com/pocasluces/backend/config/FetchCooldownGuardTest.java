package com.pocasluces.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchCooldownGuardTest {

    @Test
    void shouldAllowTheFirstFetchImmediately() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T08:00:00Z"));
        FetchCooldownGuard guard = new FetchCooldownGuard(Duration.ofMinutes(5), clock);

        assertThatCode(guard::requireCooldownElapsed).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectASecondFetchWithinTheCooldownWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T08:00:00Z"));
        FetchCooldownGuard guard = new FetchCooldownGuard(Duration.ofMinutes(5), clock);
        guard.requireCooldownElapsed();

        clock.advance(Duration.ofMinutes(4));

        assertThatThrownBy(guard::requireCooldownElapsed)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldAllowAFetchOnceTheCooldownHasElapsed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T08:00:00Z"));
        FetchCooldownGuard guard = new FetchCooldownGuard(Duration.ofMinutes(5), clock);
        guard.requireCooldownElapsed();

        clock.advance(Duration.ofMinutes(5));

        assertThatCode(guard::requireCooldownElapsed).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowOnlyOneWinnerAmongConcurrentFetches() throws InterruptedException {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T08:00:00Z"));
        FetchCooldownGuard guard = new FetchCooldownGuard(Duration.ofMinutes(5), clock);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Callable<Void> task = () -> {
            try {
                guard.requireCooldownElapsed();
                accepted.incrementAndGet();
            } catch (ResponseStatusException e) {
                rejected.incrementAndGet();
            }
            return null;
        };

        java.util.List<Future<Void>> futures = IntStream.range(0, threads)
            .mapToObj(i -> pool.submit(task))
            .toList();
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
                // assertions below cover the outcome
            }
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
