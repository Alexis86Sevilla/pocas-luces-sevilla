package com.pocasluces.backend.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDataOutageRowTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 28, 15, 38, 0);

    @Test
    void durationIsWholeMinutesOnlyWhenObservedEndIsAfterStart() {
        assertThat(OpenDataOutageRow.observedDurationMinutes(START, START.plusMinutes(7).plusSeconds(30))).isEqualTo(7L);
        assertThat(OpenDataOutageRow.observedDurationMinutes(START, START)).isNull();
        assertThat(OpenDataOutageRow.observedDurationMinutes(START, START.minusMinutes(3))).isNull();
        assertThat(OpenDataOutageRow.observedDurationMinutes(START, null)).isNull();
    }

    @Test
    void categoryUsesCauseFirstThenServiceTypeFallback() {
        assertThat(OpenDataOutageRow.category("Trabajos programados", "GB")).isEqualTo("Programado");
        assertThat(OpenDataOutageRow.category("Avería", "LV")).isEqualTo("Avería");
        assertThat(OpenDataOutageRow.category(null, "LV")).isEqualTo("Programado");
        assertThat(OpenDataOutageRow.category(null, "GB")).isEqualTo("Avería");
        assertThat(OpenDataOutageRow.category("Otra causa", "LV")).isEqualTo("Programado");
    }
}
