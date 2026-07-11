package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.dto.EnelApiFetchResult;
import com.pocasluces.backend.dto.EnelApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class EnelApiService {

    static final String ENEL_API_URL =
        "https://ineuportalgis.enel.com/server/rest/services/Hosted/ESP_Prod_power_cut_View/FeatureServer/0/query";

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1_000;

    private static final String REFERER = "https://www.e-distribucion.com/";
    private static final String USER_AGENT = "Mozilla/5.0";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EnelApiService(ObjectMapper objectMapper, @Qualifier("enelRestTemplate") RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public EnelApiFetchResult fetchSevillaOutages() {
        List<EnelApiResponse.Feature> allFeatures = new ArrayList<>();
        int offset = 0;
        int pages = 0;
        String sourceUrl = null;
        String lastRawResponse = null;

        while (pages < MAX_PAGES) {
            sourceUrl = buildUrl(offset);
            FetchPageResult page = fetchPage(sourceUrl);
            lastRawResponse = page.rawResponse();

            if (page.features().isEmpty()) {
                break;
            }

            allFeatures.addAll(page.features());
            pages++;

            if (page.features().size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }

        return new EnelApiFetchResult(sourceUrl, lastRawResponse, Collections.unmodifiableList(allFeatures));
    }

    private String buildUrl(int resultOffset) {
        return UriComponentsBuilder.fromHttpUrl(ENEL_API_URL)
            .queryParam("f", "json")
            .queryParam("where", "municipality = 'Sevilla'")
            .queryParam("outFields", "*")
            .queryParam("returnGeometry", "false")
            .queryParam("resultRecordCount", PAGE_SIZE)
            .queryParam("resultOffset", resultOffset)
            .queryParam("orderByFields", "interruption_date DESC")
            .build()
            .toUriString();
    }

    private FetchPageResult fetchPage(String sourceUrl) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Referer", REFERER);
                headers.set("User-Agent", USER_AGENT);

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(
                    sourceUrl, HttpMethod.GET, entity, String.class);

                String body = response.getBody();
                log.debug("Enel API status: {}, body preview: {}",
                    response.getStatusCode(),
                    body != null ? body.substring(0, Math.min(200, body.length())) : "null");

                validateResponse(response, body);

                EnelApiResponse apiResponse = objectMapper.readValue(body, EnelApiResponse.class);
                List<EnelApiResponse.Feature> features = apiResponse.getFeatures() != null
                    ? apiResponse.getFeatures()
                    : Collections.emptyList();
                log.info("Enel API page fetched: {} outages", features.size());
                return new FetchPageResult(features, body);
            } catch (HttpStatusCodeException e) {
                log.warn("Enel API HTTP error on attempt {}/{}: {} {}", attempt, MAX_RETRIES,
                    e.getStatusCode(), e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new EnelApiException("Enel API returned HTTP error after " + MAX_RETRIES + " attempts: " + e.getStatusCode(), e);
                }
                sleep(RETRY_DELAY_MS);
            } catch (Exception e) {
                log.error("Enel API fetch failed on attempt {}/{}: {}", attempt, MAX_RETRIES, e.getMessage(), e);
                if (attempt == MAX_RETRIES) {
                    throw new EnelApiException("Failed to fetch Enel API after " + MAX_RETRIES + " attempts", e);
                }
                sleep(RETRY_DELAY_MS);
            }
        }
        throw new IllegalStateException("Unexpected exit from retry loop");
    }

    private void validateResponse(ResponseEntity<String> response, String body) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new EnelApiException("Non-2xx response from Enel API: " + response.getStatusCode());
        }
        if (body == null || body.isBlank()) {
            throw new EnelApiException("Empty response body from Enel API");
        }
        if (body.contains("\"error\"")) {
            throw new EnelApiException("Enel API returned error in body: " + body.substring(0, Math.min(200, body.length())));
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EnelApiException("Retry sleep interrupted", e);
        }
    }

    private record FetchPageResult(List<EnelApiResponse.Feature> features, String rawResponse) {}

    public static class EnelApiException extends RuntimeException {
        public EnelApiException(String message) {
            super(message);
        }

        public EnelApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
