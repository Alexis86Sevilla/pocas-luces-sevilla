package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.EnelOutage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record OutageExportDto(
    Long id,
    String objectId,
    String neighborhoodName,
    String districtName,
    String serviceType,
    String interruptionDate,
    String repositionDate,
    Integer affectedClients,
    Double latitude,
    Double longitude,
    String sourceUrl,
    String rawResponseHash,
    String firstSeenAt,
    String fetchedAt,
    String cause,
    String resolvedAt,
    boolean brief
) {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static OutageExportDto from(EnelOutage o) {
        return new OutageExportDto(
            o.getId(),
            o.getObjectId(),
            o.getNeighborhoodName(),
            o.getDistrictName(),
            o.getServiceType(),
            formatWallClock(o.getInterruptionDate()),
            formatWallClock(o.getRepositionDate()),
            o.getAffectedClients(),
            o.getLatitude(),
            o.getLongitude(),
            o.getSourceUrl(),
            o.getRawResponseHash(),
            formatWallClock(o.getFirstSeenAt()),
            formatWallClock(o.getFetchedAt()),
            o.getCause(),
            formatWallClock(o.getResolvedAt()),
            o.isBrief()
        );
    }

    /**
     * The repository stores Europe/Madrid wall-clock values as-is (the app's Clock bean
     * is fixed to Europe/Madrid and the Endesa feed's own dates are already Madrid local
     * time). No timezone conversion is needed or correct here: just format the stored
     * value verbatim so the CSV shows exactly the time that was recorded.
     */
    private static String formatWallClock(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(ISO);
    }

    public static String header() {
        return "id,objectId,neighborhoodName,districtName,serviceType,interruptionDate,repositionDate," +
               "affectedClients,latitude,longitude,sourceUrl,rawResponseHash,firstSeenAt,fetchedAt,cause,resolvedAt,brief";
    }

    public String toCsvRow() {
        return String.join(",",
            csv(id),
            csv(objectId),
            csv(neighborhoodName),
            csv(districtName),
            csv(serviceType),
            csv(interruptionDate),
            csv(repositionDate),
            csv(affectedClients),
            csv(latitude),
            csv(longitude),
            csv(sourceUrl),
            csv(rawResponseHash),
            csv(firstSeenAt),
            csv(fetchedAt),
            csv(cause),
            csv(resolvedAt),
            csv(brief)
        );
    }

    private static String csv(Object value) {
        return CsvWriter.field(value);
    }
}
