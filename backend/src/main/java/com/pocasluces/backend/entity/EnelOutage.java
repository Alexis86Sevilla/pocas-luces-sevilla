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
     * When this outage was last observed to be missing from a successful Endesa fetch
     * (i.e. when it was marked inactive). NULL while the outage is still active. Set back
     * to NULL if the same physical outage (matched by the location key) reappears in a
     * later fetch. The real end time lies within one polling interval (~5 minutes) before
     * this timestamp; see {@code V5__add_resolved_at.sql}.
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
