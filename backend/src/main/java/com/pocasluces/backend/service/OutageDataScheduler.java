package com.pocasluces.backend.service;

import com.pocasluces.backend.dto.EnelApiResponse.Feature;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutageDataScheduler {

    private final EnelApiService enelApiService;
    private final EnelOutageRepository repository;
    private final NeighborhoodLocator locator;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Cada 30 minutos
    @Scheduled(fixedRate = 1_800_000)
    public void fetchAndSaveOutages() {
        log.info("Scheduler: fetching Enel outages for Sevilla...");

        int count = 0;
        for (Feature feature : enelApiService.fetchSevillaOutages()) {
            var attr = feature.getAttributes();
            if (attr == null || attr.getObjectId() == null) continue;

            EnelOutage outage = EnelOutage.builder()
                .objectId(attr.getObjectId())
                .latitude(attr.getLatitude())
                .longitude(attr.getLongitude())
                .affectedClients(attr.getAffectedClient())
                .serviceType(attr.getServiceType())
                .interruptionDate(parseDate(attr.getInterruptionDate()))
                .repositionDate(parseDate(attr.getRepositionDate()))
                .fetchedAt(LocalDateTime.now())
                .neighborhoodName(locator.findNeighborhood(attr.getLatitude(), attr.getLongitude()))
                .build();

            repository.save(outage);
            count++;
        }

        log.info("Scheduler: saved {} outages", count);
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
