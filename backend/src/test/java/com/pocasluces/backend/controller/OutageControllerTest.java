package com.pocasluces.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.service.EnelApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "admin.api.key=test-secret")
class OutageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnelApiService enelApiService;

    @Test
    void shouldReturnNeighborhoods() throws Exception {
        mockMvc.perform(get("/api/neighborhoods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(12)))
            .andExpect(jsonPath("$[0].name").value("San Pablo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnDistrictStats() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/stats?year=2026"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[0].districtName").value("Cerro-Amate"))
            .andExpect(jsonPath("$[0].outageCount").value(greaterThan(0)))
            .andExpect(jsonPath("$[0].averageMinutes").value(greaterThan(0.0)))
            .andExpect(jsonPath("$[*].neighborhoodName").doesNotExist())
            .andExpect(jsonPath("$[*].neighborhoodId").doesNotExist())
            .andReturn();

        List<Map<String, Object>> stats = objectMapper.readValue(
            result.getResponse().getContentAsString(), List.class);

        long previous = Long.MAX_VALUE;
        for (Map<String, Object> district : stats) {
            long count = ((Number) district.get("outageCount")).longValue();
            assertTrue(count <= previous,
                "District stats should be sorted by outage count descending");
            previous = count;
        }
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
        mockMvc.perform(get("/api/outages/export/csv?year=2099")
                .header("X-API-Key", "test-secret"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", containsString("cortes_endesa.csv")))
            .andExpect(content().string(containsString("id,objectId,neighborhoodName,districtName")));
    }

    @Test
    void shouldTriggerFetchWithValidApiKey() throws Exception {
        mockMvc.perform(post("/api/outages/fetch")
                .header("X-API-Key", "test-secret")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string("Fetch triggered. Check /api/outages/live"));
    }

    @Test
    void shouldRejectFetchWithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/outages/fetch")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
}
