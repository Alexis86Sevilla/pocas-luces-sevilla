package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.dto.EnelApiFetchResult;
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

        EnelApiFetchResult result = service.fetchSevillaOutages();

        assertThat(result.features()).hasSize(1);
        assertThat(result.features().get(0).getAttributes().getObjectId()).isEqualTo(123);
        assertThat(result.sourceUrl()).startsWith(EnelApiService.ENEL_API_URL);
        assertThat(result.rawResponse()).contains("objectid1");
    }

    @Test
    void shouldReturnEmptyListWhenNoFeatures() {
        String json = "{\"features\": []}";

        server.expect(MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        EnelApiFetchResult result = service.fetchSevillaOutages();

        assertThat(result.features()).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenFeaturesFieldIsMissing() {
        String json = "{\"objectIdFieldName\": \"objectid1\"}";

        server.expect(MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        EnelApiFetchResult result = service.fetchSevillaOutages();

        assertThat(result.features()).isEmpty();
    }

    @Test
    void shouldThrowOnApiErrorAfterRetries() {
        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withServerError());

        assertThrows(EnelApiService.EnelApiException.class, () -> service.fetchSevillaOutages());
    }

    @Test
    void shouldThrowOnApiErrorInBody() {
        String json = "{\"error\": {\"code\": 500, \"message\": \"Internal error\"}}";

        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        assertThrows(EnelApiService.EnelApiException.class, () -> service.fetchSevillaOutages());
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
