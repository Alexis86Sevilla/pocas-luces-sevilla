package com.pocasluces.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.service.FetchHealthTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HealthControllerTest {

    private static final Instant START = Instant.parse("2026-07-10T12:00:00Z");

    private MutableClock clock;
    private FetchHealthTracker tracker;
    private MockMvc mvc;

    @Autowired
    private MockMvc fullContextMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        tracker = new FetchHealthTracker(clock);
        HealthController controller = new HealthController(tracker, clock, Duration.ofMinutes(20), Duration.ofMinutes(10));
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void shouldReportStartingWithinGraceWithoutFetch() throws Exception {
        clock.advance(Duration.ofMinutes(10));

        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("STARTING"))
            .andExpect(jsonPath("$.lastSuccessfulFetch").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.ageSeconds").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void shouldReportStaleWhenNoFetchAfterGrace() throws Exception {
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        mvc.perform(get("/api/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("STALE"))
            .andExpect(jsonPath("$.lastSuccessfulFetch").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void shouldReportUpWhenFetchIsRecent() throws Exception {
        tracker.recordSuccess(12);
        clock.advance(Duration.ofMinutes(20));

        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.lastSuccessfulFetch").value("2026-07-10T12:00:00Z"))
            .andExpect(jsonPath("$.ageSeconds").value(1200))
            .andExpect(jsonPath("$.lastFeatureCount").value(12))
            .andExpect(jsonPath("$.consecutiveEmptyPolls").value(0))
            .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void shouldExposeFeedShapeCountersBeforeAndAfterEmptyPolls() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(jsonPath("$.lastFeatureCount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.consecutiveEmptyPolls").value(0));

        tracker.recordSuccess(0);
        tracker.recordSuccess(0);

        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.lastFeatureCount").value(0))
            .andExpect(jsonPath("$.consecutiveEmptyPolls").value(2));
    }

    @Test
    void shouldReportStaleWhenFetchIsTooOld() throws Exception {
        tracker.recordSuccess(3);
        clock.advance(Duration.ofMinutes(20).plusSeconds(1));

        mvc.perform(get("/api/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("STALE"))
            .andExpect(jsonPath("$.ageSeconds").value(1201));
    }

    @Test
    void shouldBePubliclyReachableInFullContext() throws Exception {
        // No API key and possibly no fetch yet: STARTING (200) or STALE (503) are both valid,
        // what matters is that the route resolves to this controller with no-store.
        fullContextMvc.perform(get("/api/health"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").exists());
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
