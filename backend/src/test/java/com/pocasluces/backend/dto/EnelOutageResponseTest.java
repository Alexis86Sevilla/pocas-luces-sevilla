package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.EnelOutage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EnelOutageResponseTest {

    private static final LocalDateTime SEEN = LocalDateTime.of(2026, 9, 28, 15, 40, 0);

    @Test
    void briefWhenResolvedAndSeenInASinglePoll() {
        assertThat(EnelOutageResponse.from(outage(SEEN, SEEN, SEEN)).brief()).isTrue();
    }

    @Test
    void notBriefWhenResolvedAfterSeveralPolls() {
        assertThat(EnelOutageResponse.from(outage(SEEN, SEEN.plusMinutes(5), SEEN.plusMinutes(5))).brief()).isFalse();
    }

    @Test
    void notBriefWhenStillActiveEvenIfSeenOnce() {
        assertThat(EnelOutageResponse.from(outage(SEEN, SEEN, null)).brief()).isFalse();
    }

    private static EnelOutage outage(LocalDateTime firstSeen, LocalDateTime fetchedAt, LocalDateTime resolvedAt) {
        return EnelOutage.builder()
            .id(1L).objectId("obj-1").neighborhoodName("San Pablo").serviceType("GB")
            .latitude(37.394512).longitude(-5.960205)
            .interruptionDate(LocalDateTime.of(2026, 9, 28, 15, 38, 0))
            .firstSeenAt(firstSeen).fetchedAt(fetchedAt).resolvedAt(resolvedAt)
            .build();
    }
}
