package com.pocasluces.backend.controller;

import com.pocasluces.backend.dto.EnelApiFetchResult;
import com.pocasluces.backend.service.EnelApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OutageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnelApiService enelApiService;

    @BeforeEach
    void setUp() {
        when(enelApiService.fetchSevillaOutages())
            .thenReturn(new EnelApiFetchResult("http://test", "{}", List.of()));
    }

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

    @Test
    void shouldReturnEmptyChartForYearWithoutFetchedData() throws Exception {
        mockMvc.perform(get("/api/outages/chart?year=2099"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnPagedEnelOutages() throws Exception {
        mockMvc.perform(get("/api/outages/enel?page=0&size=10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page.totalElements").value(greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.page.size").value(10));
    }

    @Test
    void shouldExportCsv() throws Exception {
        mockMvc.perform(get("/api/outages/export/csv?year=2099"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", containsString("cortes_endesa.csv")))
            .andExpect(content().string(containsString("id,objectId,neighborhoodName")));
    }

    @Test
    void shouldTriggerFetch() throws Exception {
        mockMvc.perform(post("/api/outages/fetch")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string("Fetch triggered. Check /api/outages/live"));
    }
}
