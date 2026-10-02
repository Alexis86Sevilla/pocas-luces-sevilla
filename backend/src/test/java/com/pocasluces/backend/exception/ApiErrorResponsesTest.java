package com.pocasluces.backend.exception;

import com.pocasluces.backend.config.FetchCooldownGuard;
import com.pocasluces.backend.service.EnelApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Client mistakes must answer a clean 4xx with the API's JSON error body, never a 500 with a
 * stack trace in the log. Runs through the full MVC stack so the dispatcher's own exceptions
 * (unknown path, missing parameter, wrong method) reach {@link GlobalExceptionHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "admin.api.key=test-secret")
class ApiErrorResponsesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnelApiService enelApiService;

    @MockBean
    private FetchCooldownGuard fetchCooldownGuard;

    @Test
    void unknownApiPathAnswers404Json() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not found"))
            .andExpect(jsonPath("$.message").value("No resource at this path"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownNestedPathAnswers404Json() throws Exception {
        mockMvc.perform(get("/api/outages/nope/deeper"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void missingRequiredParameterAnswers400Json() throws Exception {
        mockMvc.perform(get("/api/stats"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad request"))
            .andExpect(jsonPath("$.message").value(containsString("year")));
    }

    @Test
    void nonNumericParameterAnswers400Json() throws Exception {
        mockMvc.perform(get("/api/stats?year=abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Invalid value for parameter 'year'"));
    }

    @Test
    void wrongMethodAnswers405Json() throws Exception {
        mockMvc.perform(get("/api/outages/fetch"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(post("/api/health"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void adminCsvExportValidatesParametersBeforeWritingTheCsvHeader() throws Exception {
        mockMvc.perform(get("/api/outages/export/csv?year=1800").header("X-API-Key", "test-secret"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(jsonPath("$.message").value(containsString("year")));

        mockMvc.perform(get("/api/outages/export/csv?year=2026&month=13").header("X-API-Key", "test-secret"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value(containsString("month")));

        mockMvc.perform(get("/api/outages/export/csv?month=5").header("X-API-Key", "test-secret"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("month requires year"));

        mockMvc.perform(get("/api/outages/export/csv?year=abc").header("X-API-Key", "test-secret"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
