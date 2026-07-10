package com.pocasluces.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "enel_outages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnelOutage {

    @Id
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

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "neighborhood_name", length = 100)
    private String neighborhoodName;
}
