package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.entity.OutageView;

import java.time.LocalDateTime;

public record EnelOutageResponse(
    Long id,
    String objectId,
    String neighborhoodName,
    String districtName,
    String serviceType,
    String cause,
    LocalDateTime interruptionDate,
    LocalDateTime repositionDate,
    Integer affectedClients,
    Double latitude,
    Double longitude,
    LocalDateTime firstSeenAt,
    LocalDateTime fetchedAt,
    LocalDateTime resolvedAt,
    boolean brief
) {

    /** For callers that hold the entity (the native {@code /live} read). */
    public static EnelOutageResponse from(EnelOutage outage) {
        return outage == null ? null : from(OutageView.of(outage));
    }

    public static EnelOutageResponse from(OutageView outage) {
        if (outage == null) {
            return null;
        }
        return new EnelOutageResponse(
            outage.getId(),
            outage.getObjectId(),
            outage.getNeighborhoodName(),
            outage.getDistrictName(),
            outage.getServiceType(),
            outage.getCause(),
            outage.getInterruptionDate(),
            outage.getRepositionDate(),
            outage.getAffectedClients(),
            outage.getLatitude(),
            outage.getLongitude(),
            outage.getFirstSeenAt(),
            outage.getFetchedAt(),
            outage.getResolvedAt(),
            outage.isBrief()
        );
    }
}
