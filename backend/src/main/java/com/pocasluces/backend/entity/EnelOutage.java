package com.pocasluces.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "enel_outages",
    indexes = {
        @Index(name = "idx_enel_outage_object_id", columnList = "object_id"),
        @Index(name = "idx_enel_outage_interruption_date", columnList = "interruption_date"),
        @Index(name = "idx_enel_outage_neighborhood", columnList = "neighborhood_name"),
        @Index(name = "idx_enel_outage_district", columnList = "district_name"),
        @Index(name = "idx_enel_outage_fetched_at", columnList = "fetched_at"),
        @Index(name = "idx_enel_outage_resolved_at", columnList = "resolved_at")
    },
    uniqueConstraints = {
        // Identity is based on where and when the outage happened, not on our own
        // neighborhood inference (derived from these same coordinates) and not on
        // Endesa's objectId (not stable across layer republishes; abandoned as an
        // identity source in July 2026, see commit 3de153d).
        @UniqueConstraint(name = "uk_enel_outage_location_key", columnNames = {"latitude", "longitude", "interruption_date", "service_type"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnelOutage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id", nullable = false, length = 50)
    private String objectId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "affected_clients")
    private Integer affectedClients;

    @Column(name = "service_type", nullable = false, length = 10)
    private String serviceType;

    @Column(name = "interruption_date", nullable = false)
    private LocalDateTime interruptionDate;

    @Column(name = "reposition_date")
    private LocalDateTime repositionDate;

    @Column(name = "neighborhood_name", nullable = false, length = 100)
    private String neighborhoodName;

    @Column(name = "district_name", length = 100)
    private String districtName;

    /** Endesa's own cause label (feed field {@code des_cause_es}), e.g. "Avería" or "Trabajos programados". */
    @Column(name = "cause", length = 255)
    private String cause;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "raw_response_hash", length = 64)
    private String rawResponseHash;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * Last poll in which Endesa still published this outage (its {@code fetchedAt} at the
     * moment it was marked inactive). NULL while the outage is still active. Set back to
     * NULL if the same physical outage (matched by the location key) reappears in a later
     * fetch. It is a lower bound: the real end happened up to one polling interval
     * (~5 minutes) later, or more if our polling had a gap; see {@code V5__add_resolved_at.sql}.
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Whether the public Telegram alerts may ever announce this outage. FALSE for every
     * row that already existed when the alerts went live (backfilled by
     * {@code V6__add_telegram_announcement_state.sql}), TRUE for rows inserted since.
     * Never updated by the upsert.
     */
    @Column(name = "announce_eligible", nullable = false)
    @Builder.Default
    private boolean announceEligible = true;

    /** Poll in which the "new outage" Telegram message was confirmed sent; NULL until then. */
    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    /** Poll in which the "power restored" Telegram message was confirmed sent; NULL until then. */
    @Column(name = "restoration_announced_at")
    private LocalDateTime restorationAnnouncedAt;

    /**
     * Consecutive successful polls in which an already announced outage has not been
     * published by Endesa. Incremented by the scheduler after its resolve step, reset to 0
     * by the upsert when the outage reappears; see {@code OutageAnnouncer}.
     */
    @Column(name = "missing_polls", nullable = false)
    @Builder.Default
    private int missingPolls = 0;

    /**
     * Start time Endesa published the first time this outage was seen, kept only when a
     * later poll republished the same outage with a corrected start (see
     * {@code OutageDataScheduler}, start-time corrections). NULL for the vast majority of
     * rows, whose {@code interruptionDate} was never corrected. Set once and never
     * overwritten, so the original value stays auditable through later corrections.
     * Backfilled by {@code V8__merge_start_corrected_duplicates.sql} for merged rows.
     */
    @Column(name = "original_interruption_date")
    private LocalDateTime originalInterruptionDate;

    /** Poll (Europe/Madrid wall-clock) of the latest start-time correction; NULL if none. */
    @Column(name = "start_corrected_at")
    private LocalDateTime startCorrectedAt;

    /**
     * True for an outage that was published in exactly one poll and was already gone in the
     * next one: resolved, and its last sighting is also its first. An active outage seen
     * once is never brief, since it may still be ongoing. Derived, not stored.
     */
    public boolean isBrief() {
        return resolvedAt != null && fetchedAt != null && fetchedAt.equals(firstSeenAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnelOutage that)) return false;
        return Objects.equals(latitude, that.latitude)
            && Objects.equals(longitude, that.longitude)
            && Objects.equals(interruptionDate, that.interruptionDate)
            && Objects.equals(serviceType, that.serviceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, interruptionDate, serviceType);
    }
}
