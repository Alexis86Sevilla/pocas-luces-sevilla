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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Polls Endesa's feed every 5 minutes and reconciles the stored outages with it. The site must
 * never publish inflated or false data, so a poll is applied only when the feed was read
 * completely and parsed cleanly, and two situations get special care:
 *
 * <h2>Start-time corrections</h2>
 * Endesa sometimes republishes the same physical outage with a corrected start, which changes
 * its identity key {@code (latitude, longitude, interruption_date, service_type)}. Taken
 * literally that would insert a second row and resolve the first in the same poll (usually as a
 * false "brief" outage). The scheduler instead treats it as the same outage when the match is
 * unambiguous: the poll contains exactly one feature with a new key at a point and service type
 * where exactly one active row vanished, and the new start is not after that row's last
 * sighting. The existing row is then moved to the corrected key (keeping its id,
 * {@code first_seen_at} and announcement state, recording the original start) and the ordinary
 * upsert updates it as a re-sighting. Anything ambiguous falls back to the literal behaviour.
 *
 * <h2>Mass-resolution guard</h2>
 * Resolving means "Endesa no longer publishes it". When a single poll would resolve every active
 * outage, or more than half of them when at least {@link #MASS_RESOLUTION_MIN_ACTIVE} were
 * active, the feed may simply be incomplete, so the resolution waits until the same condition
 * holds on two consecutive successful polls. {@code resolved_at} is unaffected by the wait (it
 * is each row's last sighting), only the {@code active} flag flips one poll later.
 */
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

    /** Above this share of skipped (unparseable) features the poll is not applied. */
    static final double MAX_SKIPPED_RATIO = 0.20;
    /** From this many active rows on, resolving more than half of them needs two polls. */
    static final int MASS_RESOLUTION_MIN_ACTIVE = 4;

    private final EnelApiService enelApiService;
    private final EnelOutageRepository repository;
    private final NeighborhoodLocator locator;
    private final DistrictLocator districtLocator;
    private final Clock clock;
    private final FetchHealthTracker fetchHealthTracker;
    private final OutageAnnouncer announcer;
    private final WeeklySummaryAnnouncer weeklySummaryAnnouncer;

    /**
     * True when the previous successful poll met the mass-resolution condition and its resolve
     * step was deferred. In memory only: after a restart the guard simply needs two polls again.
     */
    private boolean massResolutionPending;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public synchronized void fetchAndSaveOutages() {
        log.info("Scheduler: fetching Enel outages for Sevilla...");

        List<EnelApiFeatureWithEvidence> pagedFeatures;
        try {
            pagedFeatures = enelApiService.fetchSevillaOutages();
        } catch (EnelApiService.EnelApiException e) {
            log.error("Scheduler: failed to fetch Enel API, will retry on next run: {}", e.getMessage());
            massResolutionPending = false;
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // Parse everything before writing anything, so a feed whose rows no longer parse
        // (renamed field, new date format) is rejected as a whole instead of being applied as
        // a mostly empty poll that resolves the outages it failed to read.
        List<EnelOutage> parsed = new ArrayList<>(pagedFeatures.size());
        int skipped = 0;
        for (EnelApiFeatureWithEvidence paged : pagedFeatures) {
            EnelOutage outage = toOutage(paged, now);
            if (outage == null) {
                skipped++;
            } else {
                parsed.add(outage);
            }
        }
        if (skipped > 0 && skipped > pagedFeatures.size() * MAX_SKIPPED_RATIO) {
            log.error("Scheduler: {} of {} features could not be parsed (above {}%); not applying this poll",
                skipped, pagedFeatures.size(), Math.round(MAX_SKIPPED_RATIO * 100));
            massResolutionPending = false;
            return;
        }

        long activeBefore = repository.countByActiveTrue();
        int corrected = applyStartCorrections(parsed, now);

        for (EnelOutage outage : parsed) {
            repository.upsert(outage);
        }
        int saved = parsed.size();

        // Outages not re-upserted by this run were not reported in the fetch we just
        // processed: mark them resolved now, unless the resolution is suspiciously large and
        // this is the first poll that says so (see the class javadoc).
        int resolved = resolveStaleOutages(now, activeBefore, pagedFeatures.isEmpty());

        // Telegram restoration tracking: every announced outage that this successful run did
        // not see (inactive after the resolve step) has now been missing for one more
        // consecutive poll. Same transaction as the resolve step, so the counter and the
        // active flag always agree; a reappearance resets it through the upsert above.
        int pendingRestorations = repository.incrementMissingPollsOfAnnouncedInactiveOutages();

        log.info("Scheduler: saved {} outages ({} skipped, {} start correction(s)), resolved {} outage(s) no longer reported, {} announced outage(s) pending restoration",
            saved, skipped, corrected, resolved, pendingRestorations);

        // Only reached when the fetch succeeded and everything was applied. Recorded after the
        // transaction commits (see FetchHealthTracker), so a rollback is not a success.
        fetchHealthTracker.recordSuccess(pagedFeatures.size());
        // Registered after the health tracker so its hook always runs first; the announcer
        // never throws, and Telegram problems never touch this transaction.
        announcer.announceAfterCommit();
        // Registered after the alerts so it always runs after them (and skips this poll if they met a
        // Telegram problem); never throws.
        weeklySummaryAnnouncer.announceAfterCommit(announcer::telegramHealthy);
    }

    /**
     * Detects unambiguous start-time corrections (see the class javadoc) and moves the matched
     * existing rows to their corrected key before the upserts run.
     *
     * @return the number of rows corrected
     */
    private int applyStartCorrections(List<EnelOutage> parsed, LocalDateTime now) {
        List<EnelOutage> active = repository.findAllActive();
        if (active.isEmpty() || parsed.isEmpty()) {
            return 0;
        }

        Set<EnelOutage> pollKeys = new HashSet<>(parsed); // equals/hashCode = identity key
        Map<PointKey, List<EnelOutage>> vanishedByPoint = new HashMap<>();
        for (EnelOutage row : active) {
            if (!pollKeys.contains(row)) {
                vanishedByPoint.computeIfAbsent(PointKey.of(row), k -> new ArrayList<>()).add(row);
            }
        }
        if (vanishedByPoint.isEmpty()) {
            return 0;
        }

        Set<EnelOutage> activeKeys = new HashSet<>(active);
        Map<PointKey, List<EnelOutage>> newByPoint = new HashMap<>();
        for (EnelOutage outage : parsed) {
            PointKey point = PointKey.of(outage);
            if (activeKeys.contains(outage) || !vanishedByPoint.containsKey(point)) {
                continue;
            }
            newByPoint.computeIfAbsent(point, k -> new ArrayList<>()).add(outage);
        }

        int corrected = 0;
        for (Map.Entry<PointKey, List<EnelOutage>> entry : newByPoint.entrySet()) {
            List<EnelOutage> vanished = vanishedByPoint.get(entry.getKey());
            List<EnelOutage> candidates = entry.getValue();
            if (vanished.size() != 1 || candidates.size() != 1) {
                log.info("Scheduler: {} vanished and {} new outage(s) at the same point/type; ambiguous, no start correction",
                    vanished.size(), candidates.size());
                continue;
            }
            EnelOutage existing = vanished.get(0);
            EnelOutage incoming = candidates.get(0);
            if (incoming.getInterruptionDate().isAfter(existing.getFetchedAt())) {
                continue; // started after the old row was last seen: a genuine new outage
            }
            boolean keyTaken = repository.findByLatitudeAndLongitudeAndInterruptionDateAndServiceType(
                incoming.getLatitude(), incoming.getLongitude(), incoming.getInterruptionDate(),
                incoming.getServiceType()).isPresent();
            if (keyTaken) {
                continue; // an older (resolved) row already owns the corrected key: plain re-sighting
            }
            int updated = repository.correctInterruptionDate(existing.getId(), incoming.getInterruptionDate(), now);
            if (updated == 1) {
                corrected++;
                log.warn("Scheduler: start-time correction at ({}, {}, {}): {} -> {} (row id {}, kept as the same outage)",
                    existing.getLatitude(), existing.getLongitude(), existing.getServiceType(),
                    existing.getInterruptionDate(), incoming.getInterruptionDate(), existing.getId());
            }
        }
        return corrected;
    }

    /** Resolve step with the mass-resolution guard; returns the number of rows resolved. */
    private int resolveStaleOutages(LocalDateTime now, long activeBefore, boolean emptyFeed) {
        long wouldResolve = repository.countActiveNotFetchedAt(now);
        boolean mass = wouldResolve > 0
            && (wouldResolve >= activeBefore
                || (activeBefore >= MASS_RESOLUTION_MIN_ACTIVE && wouldResolve * 2 > activeBefore));

        if (mass && !massResolutionPending) {
            armMassResolutionDeferral();
            log.warn("Scheduler: this poll would resolve {} of {} active outage(s){}; deferring until the next poll confirms it",
                wouldResolve, activeBefore, emptyFeed ? " (feed returned zero outages)" : "");
            return 0;
        }
        massResolutionPending = false;

        int resolved = repository.resolveStaleActiveOutages(now);
        if (mass) {
            log.warn("Scheduler: confirmed on two consecutive polls; marking {} of {} previously active outage(s) as resolved{}",
                resolved, activeBefore, emptyFeed ? " (feed returned zero outages)" : "");
        }
        return resolved;
    }

    /**
     * Arms the two-poll guard and disarms it again if this run rolls back: a deferral that was
     * never committed must not count as the first of the two polls.
     */
    private void armMassResolutionDeferral() {
        massResolutionPending = true;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        massResolutionPending = false;
                    }
                }
            });
        }
    }

    /** Maps a feed feature to an outage row, or null when it cannot be used (counted as skipped). */
    private EnelOutage toOutage(EnelApiFeatureWithEvidence paged, LocalDateTime now) {
        EnelApiResponse.Feature feature = paged.feature();
        var attr = feature.getAttributes();
        if (attr == null || attr.getObjectId() == null) {
            return null;
        }

        LocalDateTime interruptionDate = parseDate(attr.getInterruptionDate());
        if (interruptionDate == null) {
            log.warn("Skipping outage objectId={} due to unparseable interruptionDate: {}",
                attr.getObjectId(), attr.getInterruptionDate());
            return null;
        }

        double lat = attr.getLatitude() != null ? attr.getLatitude() : 0.0;
        double lon = attr.getLongitude() != null ? attr.getLongitude() : 0.0;
        String neighborhoodName = normalizeNeighborhood(locator.findNeighborhood(lat, lon));
        String districtName = normalizeDistrict(districtLocator.findDistrict(lat, lon, neighborhoodName));
        String serviceType = normalizeServiceType(attr.getServiceType());

        return EnelOutage.builder()
            .objectId(attr.getObjectId())
            .latitude(lat)
            .longitude(lon)
            .affectedClients(attr.getAffectedClient())
            .serviceType(serviceType)
            .interruptionDate(interruptionDate)
            .repositionDate(parseDate(attr.getRepositionDate()))
            .neighborhoodName(neighborhoodName)
            .districtName(districtName)
            .cause(attr.getCause())
            .sourceUrl(paged.sourceUrl())
            .rawResponse(paged.rawResponse())
            .rawResponseHash(sha256Hex(paged.rawResponse()))
            .firstSeenAt(now)
            .fetchedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
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

    private String normalizeDistrict(String districtName) {
        return districtName == null || districtName.isBlank() ? "Zona no identificada" : districtName;
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

    /** Where and what kind: the identity key without the start time. */
    private record PointKey(Double latitude, Double longitude, String serviceType) {
        static PointKey of(EnelOutage o) {
            return new PointKey(o.getLatitude(), o.getLongitude(), o.getServiceType());
        }
    }
}
