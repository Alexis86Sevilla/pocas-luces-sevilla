package com.pocasluces.backend.service;

import com.pocasluces.backend.dto.EnelApiFeatureWithEvidence;
import com.pocasluces.backend.dto.EnelApiResponse;
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
    private final Clock clock;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void fetchAndSaveOutages() {
        log.info("Scheduler: fetching Enel outages for Sevilla...");

        List<EnelApiFeatureWithEvidence> pagedFeatures;
        try {
            pagedFeatures = enelApiService.fetchSevillaOutages();
        } catch (EnelApiService.EnelApiException e) {
            log.error("Scheduler: failed to fetch Enel API, will retry on next run: {}", e.getMessage());
            return;
        }

        int saved = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now(clock);

        repository.setAllInactive();

        for (EnelApiFeatureWithEvidence paged : pagedFeatures) {
            EnelApiResponse.Feature feature = paged.feature();
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

            double lat = attr.getLatitude() != null ? attr.getLatitude() : 0.0;
            double lon = attr.getLongitude() != null ? attr.getLongitude() : 0.0;
            String neighborhoodName = normalizeNeighborhood(locator.findNeighborhood(lat, lon));
            String serviceType = normalizeServiceType(attr.getServiceType());

            EnelOutage outage = EnelOutage.builder()
                .objectId(attr.getObjectId())
                .latitude(attr.getLatitude())
                .longitude(attr.getLongitude())
                .affectedClients(attr.getAffectedClient())
                .serviceType(serviceType)
                .interruptionDate(interruptionDate)
                .repositionDate(parseDate(attr.getRepositionDate()))
                .neighborhoodName(neighborhoodName)
                .sourceUrl(paged.sourceUrl())
                .rawResponse(paged.rawResponse())
                .rawResponseHash(sha256Hex(paged.rawResponse()))
                .firstSeenAt(now)
                .fetchedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

            repository.upsert(outage);
            saved++;
        }

        log.info("Scheduler: saved {} outages ({} skipped)", saved, skipped);
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

    private String normalizeNeighborhood(String neighborhoodName) {
        return neighborhoodName == null || neighborhoodName.isBlank() ? "Zona no identificada" : neighborhoodName;
    }

    private String normalizeServiceType(String serviceType) {
        return serviceType == null || serviceType.isBlank() ? "UNKNOWN" : serviceType;
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
