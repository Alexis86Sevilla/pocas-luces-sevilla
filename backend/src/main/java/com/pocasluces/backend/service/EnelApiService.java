package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.dto.EnelApiResponse;
import com.pocasluces.backend.dto.EnelApiResponse.Feature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class EnelApiService {

    private static final String ENEL_API_URL =
        "https://ineuportalgis.enel.com/server/rest/services/Hosted/ESP_Prod_power_cut_View/FeatureServer/0/query";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EnelApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public List<Feature> fetchSevillaOutages() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(ENEL_API_URL)
                .queryParam("f", "json")
                .queryParam("where", "municipality = 'Sevilla'")
                .queryParam("outFields", "*")
                .queryParam("returnGeometry", "false")
                .queryParam("resultRecordCount", "100")
                .queryParam("orderByFields", "interruption_date DESC")
                .build()
                .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://www.e-distribucion.com/");
            headers.set("User-Agent", "Mozilla/5.0");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            log.info("Enel API status: {}, body preview: {}",
                response.getStatusCode(),
                body != null ? body.substring(0, Math.min(200, body.length())) : "null");

            if (body != null && response.getStatusCode().is2xxSuccessful() && !body.contains("\"error\"")) {
                EnelApiResponse apiResponse = objectMapper.readValue(body, EnelApiResponse.class);
                if (apiResponse.getFeatures() != null) {
                    log.info("Enel API: {} outages fetched", apiResponse.getFeatures().size());
                    return apiResponse.getFeatures();
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Enel API: {}", e.getMessage(), e);
        }
        return Collections.emptyList();
    }
}
