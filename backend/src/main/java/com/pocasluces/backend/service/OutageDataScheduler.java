package com.pocasluces.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.dto.EnelApiFetchResult;
import com.pocasluces.backend.dto.EnelApiResponse.Feature;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutageDataScheduler {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    );

    private final EnelApiService enelApiService;
    private final EnelOutageRepository repository;
    private final NeighborhoodLocator locator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void fetchAndSaveOutages() {
        log.info("Scheduler: fetching Enel outages for Sevilla...");

        EnelApiFetchResult result;
        try {
            result = enelApiService.fetchSevillaOutages();
        } catch (EnelApiService.EnelApiException e) {
            log.error("Scheduler: failed to fetch Enel API, will retry on next run: {}", e.getMessage());
            return;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now(clock);

        for (Feature feature : result.features()) {
            var attr = feature.getAttributes();
            if (attr == null || attr.getObjectId() == null) {
                skipped++;
                continue;
            }

            LocalDateTime interruptionDate = parseDate(attr.getInterruptionDate());
            if (interruptionDate == null) {
                log.warn("Skipping outage objectId={} due to unparseable interruptionDate: {}",
                    attr.getObjectId(), attr.getInterruptionDate());
                skipped++;
                continue;
            }

            Optional<EnelOutage> existing = repository.findByObjectId(attr.getObjectId());
            EnelOutage outage = existing.orElseGet(EnelOutage::new);

            double lat = attr.getLatitude() != null ? attr.getLatitude() : 0.0;
            double lon = attr.getLongitude() != null ? attr.getLongitude() : 0.0;
            String neighborhoodName = locator.findNeighborhood(lat, lon);

            String featureJson = serializeFeature(feature, attr.getObjectId());

            outage.setObjectId(attr.getObjectId());
            outage.setLatitude(attr.getLatitude());
            outage.setLongitude(attr.getLongitude());
            outage.setAffectedClients(attr.getAffectedClient());
            outage.setServiceType(attr.getServiceType());
            outage.setInterruptionDate(interruptionDate);
            outage.setRepositionDate(parseDate(attr.getRepositionDate()));
            outage.setNeighborhoodName(neighborhoodName);
            outage.setSourceUrl(result.sourceUrl());
            outage.setRawResponse(featureJson);
            outage.setRawResponseHash(sha256Hex(featureJson));
            outage.setFetchedAt(now);
            outage.setUpdatedAt(now);

            if (existing.isEmpty()) {
                outage.setCreatedAt(now);
                outage.setFirstSeenAt(now);
                inserted++;
            } else {
                updated++;
            }

            repository.save(outage);
        }

        log.info("Scheduler: saved {} outages ({} inserted, {} updated, {} skipped)",
            inserted + updated, inserted, updated, skipped);
    }

    private String serializeFeature(Feature feature, Long objectId) {
        try {
            return objectMapper.writeValueAsString(feature);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize feature objectId={}, storing empty raw response", objectId, e);
            return "{}";
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String trimmed = dateStr.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                // try next format
            }
        }
        log.warn("Could not parse date '{}'", dateStr);
        return null;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
