package com.pocasluces.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OutageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNeighborhoods() throws Exception {
        mockMvc.perform(get("/api/neighborhoods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(12)))
            .andExpect(jsonPath("$[0].name").value("San Pablo"));
    }

    @Test
    void shouldReturnStats() throws Exception {
        mockMvc.perform(get("/api/stats?year=2026"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldReturnTestimonials() throws Exception {
        mockMvc.perform(get("/api/testimonials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void shouldReturnLiveOutages() throws Exception {
        mockMvc.perform(get("/api/outages/live"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
