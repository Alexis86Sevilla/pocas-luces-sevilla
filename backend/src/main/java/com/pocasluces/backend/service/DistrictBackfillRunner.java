package com.pocasluces.backend.service;

import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Backfills {@code districtName} for existing {@link EnelOutage} rows that were
 * created before the district column existed or that somehow missed district resolution.
 *
 * <p>Runs once after the application context is ready. It is idempotent: rows whose
 * {@code districtName} is already populated are skipped, and rows with invalid
 * coordinates are left as {@code "Zona no identificada"}.</p>
 */
@Slf4j
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class DistrictBackfillRunner {

    private static final int BATCH_SIZE = 100;
    private static final String UNKNOWN_DISTRICT = "Zona no identificada";

    private final EnelOutageRepository repository;
    private final DistrictLocator districtLocator;

    @PostConstruct
    @Transactional
    public void backfill() {
        log.info("District backfill: starting");

        long total = repository.count();
        long pending = countPending();
        log.info("District backfill: {} total rows, {} pending", total, pending);

        if (pending == 0) {
            log.info("District backfill: nothing to do");
            return;
        }

        long processed = 0;
        long updated = 0;
        long skipped = 0;
        long lastId = 0L;

        List<EnelOutage> batch;
        do {
            batch = findNextBatch(lastId);
            for (EnelOutage outage : batch) {
                lastId = outage.getId();
                processed++;

                String district = resolveDistrict(outage);
                outage.setDistrictName(district);
                repository.save(outage);
                updated++;
                if (UNKNOWN_DISTRICT.equals(district)) {
                    skipped++;
                }
            }
            log.info("District backfill: processed {} rows, updated {}, skipped {}",
                processed, updated, skipped);
        } while (!batch.isEmpty());

        log.info("District backfill: completed — processed {}, updated {}, skipped {}",
            processed, updated, skipped);
    }

    private long countPending() {
        return repository.countByDistrictNameIsNull();
    }

    private List<EnelOutage> findNextBatch(long afterId) {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        return repository.findTopBatchWithNullDistrict(afterId, pageable);
    }

    private String resolveDistrict(EnelOutage outage) {
        Double lat = outage.getLatitude();
        Double lon = outage.getLongitude();
        String neighborhoodName = outage.getNeighborhoodName();

        if (lat == null || lon == null) {
            return UNKNOWN_DISTRICT;
        }

        double latValue = lat;
        double lonValue = lon;
        if (latValue == 0.0 && lonValue == 0.0) {
            return UNKNOWN_DISTRICT;
        }

        String district = districtLocator.findDistrict(latValue, lonValue, neighborhoodName);
        return district == null || district.isBlank() ? UNKNOWN_DISTRICT : district;
    }
}
