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
    void shouldFailWhenFeaturesFieldIsMissingInsteadOfReportingAnOutageFreeCity() {
        // A reshaped response without "features" is not "zero outages": returning an empty
        // list here would make the scheduler resolve every active outage.
        String json = "{\"objectIdFieldName\": \"objectid1\"}";

        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(Matchers.startsWith(EnelApiService.ENEL_API_URL)))
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        EnelApiService.EnelApiException ex = assertThrows(EnelApiService.EnelApiException.class,
            () -> service.fetchSevillaOutages());

        assertThat(ex.getMessage()).contains("no 'features' array");
        server.verify();
    }

    @Test
    void shouldKeepPagingWhileTheFeedFlagsExceededTransferLimitEvenOnShortPages() {
        // Server-side record limit below our page size: 1 feature per page, flagged as cut.
        String firstPage = """
            {"exceededTransferLimit": true, "features": [{"attributes": {"objectid1": 1, "interruption_date": "10/07/2026 08:30"}}]}
            """;
        String secondPage = """
            {"exceededTransferLimit": false, "features": [{"attributes": {"objectid1": 2, "interruption_date": "10/07/2026 08:40"}}]}
            """;

        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(Matchers.containsString("resultOffset=0")))
            .andRespond(MockRestResponseCreators.withSuccess(firstPage, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(Matchers.containsString("resultOffset=1")))
            .andRespond(MockRestResponseCreators.withSuccess(secondPage, MediaType.APPLICATION_JSON));

        List<EnelApiFeatureWithEvidence> result = service.fetchSevillaOutages();

        assertThat(result).extracting(f -> f.feature().getAttributes().getObjectId()).containsExactly("1", "2");
        server.verify();
    }

    @Test
    void shouldFailWhenTheLastPageStillReportsMoreRecordsThanFetched() {
        String firstPage = """
            {"exceededTransferLimit": true, "features": [{"attributes": {"objectid1": 1, "interruption_date": "10/07/2026 08:30"}}]}
            """;
        String emptyButCut = "{\"exceededTransferLimit\": true, \"features\": []}";

        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(Matchers.containsString("resultOffset=0")))
            .andRespond(MockRestResponseCreators.withSuccess(firstPage, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(Matchers.containsString("resultOffset=1")))
            .andRespond(MockRestResponseCreators.withSuccess(emptyButCut, MediaType.APPLICATION_JSON));

        EnelApiService.EnelApiException ex = assertThrows(EnelApiService.EnelApiException.class,
            () -> service.fetchSevillaOutages());

        assertThat(ex.getMessage()).contains("exceededTransferLimit");
        server.verify();
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
            .andExpect(MockRestRequestMatchers.header("User-Agent", "SevillaSinLuz/1.0 (+https://sevillasinluz.es)"))
            .andExpect(request -> assertThat(request.getHeaders().get("Referer")).isNull())
            .andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));

        service.fetchSevillaOutages();
    }
}
