package com.pocasluces.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "enel_outages",
    indexes = {
        @Index(name = "idx_enel_outage_object_id", columnList = "object_id"),
        @Index(name = "idx_enel_outage_interruption_date", columnList = "interruption_date"),
        @Index(name = "idx_enel_outage_neighborhood", columnList = "neighborhood_name"),
        @Index(name = "idx_enel_outage_fetched_at", columnList = "fetched_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_enel_outage_object_id", columnNames = "object_id")
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
    private Long objectId;

    private Double latitude;
    private Double longitude;

    @Column(name = "affected_clients")
    private Integer affectedClients;

    @Column(name = "service_type", length = 10)
    private String serviceType;

    @Column(name = "interruption_date")
    private LocalDateTime interruptionDate;

    @Column(name = "reposition_date")
    private LocalDateTime repositionDate;

    @Column(name = "neighborhood_name", length = 100)
    private String neighborhoodName;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
