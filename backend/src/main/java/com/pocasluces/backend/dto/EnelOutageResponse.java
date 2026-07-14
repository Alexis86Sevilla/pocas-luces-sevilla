package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.EnelOutage;

import java.time.LocalDateTime;

public record EnelOutageResponse(
    Long id,
    String objectId,
    String neighborhoodName,
    String districtName,
    String serviceType,
    LocalDateTime interruptionDate,
    LocalDateTime repositionDate,
    Integer affectedClients,
    Double latitude,
    Double longitude,
    LocalDateTime firstSeenAt,
    LocalDateTime fetchedAt
) {

    public static EnelOutageResponse from(EnelOutage outage) {
        if (outage == null) {
            return null;
        }
        return new EnelOutageResponse(
            outage.getId(),
            outage.getObjectId(),
            outage.getNeighborhoodName(),
            outage.getDistrictName(),
            outage.getServiceType(),
            outage.getInterruptionDate(),
            outage.getRepositionDate(),
            outage.getAffectedClients(),
            outage.getLatitude(),
            outage.getLongitude(),
            outage.getFirstSeenAt(),
            outage.getFetchedAt()
        );
    }
}
