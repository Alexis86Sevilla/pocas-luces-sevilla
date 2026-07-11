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
import java.util.Optional;

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

        int inserted = 0;
        int updated = 0;
        for (Feature feature : enelApiService.fetchSevillaOutages()) {
            var attr = feature.getAttributes();
            if (attr == null || attr.getObjectId() == null) continue;

            String neighborhoodName = locator.findNeighborhood(attr.getLatitude(), attr.getLongitude());
            LocalDateTime interruptionDate = parseDate(attr.getInterruptionDate());
            String serviceType = attr.getServiceType();

            Optional<EnelOutage> existing = repository.findByNeighborhoodNameAndInterruptionDateAndServiceType(
                    neighborhoodName, interruptionDate, serviceType);

            EnelOutage outage = existing.orElseGet(EnelOutage::new);
            outage.setObjectId(attr.getObjectId());
            outage.setLatitude(attr.getLatitude());
            outage.setLongitude(attr.getLongitude());
            outage.setAffectedClients(attr.getAffectedClient());
            outage.setServiceType(serviceType);
            outage.setInterruptionDate(interruptionDate);
            outage.setRepositionDate(parseDate(attr.getRepositionDate()));
            outage.setFetchedAt(LocalDateTime.now());
            outage.setNeighborhoodName(neighborhoodName);

            repository.save(outage);

            if (existing.isPresent()) {
                updated++;
            } else {
                inserted++;
            }
        }

        log.info("Scheduler: saved {} outages ({} inserted, {} updated)", inserted + updated, inserted, updated);
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
