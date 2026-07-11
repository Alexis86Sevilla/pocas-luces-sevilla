package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.dto.EnelApiFeatureWithEvidence;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnelApiServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EnelApiService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new EnelApiService(new ObjectMapper(), restTemplate);
    }

    @Test
    void shouldReturnFeaturesOnSuccessfulResponse() {
        String json = """
            {
              "features": [
                {
                  "attributes": {
                    "objectid1": 123,
                    "municipality": "Sevilla",
                    "service_type": "AT",
                    "interruption_date": "10/07/2026 08:30"
                  }
                }
              ]
            }
            """;

        server.expect(MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        List<EnelApiFeatureWithEvidence> result = service.fetchSevillaOutages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).feature().getAttributes().getObjectId()).isEqualTo("123");
        assertThat(result.get(0).sourceUrl()).startsWith(EnelApiService.ENEL_API_URL);
        assertThat(result.get(0).rawResponse()).contains("objectid1");
    }

    @Test
    void shouldReturnEmptyListWhenNoFeatures() {
        String json = "{\"features\": []}";

        server.expect(MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        List<EnelApiFeatureWithEvidence> result = service.fetchSevillaOutages();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenFeaturesFieldIsMissing() {
        String json = "{\"objectIdFieldName\": \"objectid1\"}";

        server.expect(MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        List<EnelApiFeatureWithEvidence> result = service.fetchSevillaOutages();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowOnApiErrorAfterRetries() {
        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withServerError());

        assertThrows(EnelApiService.EnelApiException.class, () -> service.fetchSevillaOutages());
    }

    @Test
    void shouldThrowOnParsedArcGisErrorInBody() {
        String json = "{\"error\": {\"code\": 500, \"message\": \"Internal error\", \"details\": [\"detail\"]}}";

        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        EnelApiService.EnelApiException ex = assertThrows(EnelApiService.EnelApiException.class,
            () -> service.fetchSevillaOutages());
        assertThat(ex.getMessage()).contains("code=500").contains("Internal error");
    }

    @Test
    void shouldSetExpectedHeaders() {
        String json = "{\"features\": []}";

        server.expect(MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andExpect(MockRestRequestMatchers.header("Referer", "https://www.e-distribucion.com/"))
            .andExpect(MockRestRequestMatchers.header("User-Agent", "Mozilla/5.0"))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        service.fetchSevillaOutages();
    }
}
