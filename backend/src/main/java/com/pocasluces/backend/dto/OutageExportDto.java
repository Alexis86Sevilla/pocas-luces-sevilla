package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.EnelOutage;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    String fetchedAt
) {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    public static OutageExportDto from(EnelOutage o) {
        return new OutageExportDto(
            o.getId(),
            o.getObjectId(),
            o.getNeighborhoodName(),
            o.getDistrictName(),
            o.getServiceType(),
            toMadridWallClock(o.getInterruptionDate()),
            toMadridWallClock(o.getRepositionDate()),
            o.getAffectedClients(),
            o.getLatitude(),
            o.getLongitude(),
            o.getSourceUrl(),
            o.getRawResponseHash(),
            toMadridWallClock(o.getFirstSeenAt()),
            toMadridWallClock(o.getFetchedAt())
        );
    }

    /**
     * The repository stores UTC-equivalent instants in LocalDateTime columns
     * (due to Timestamp.valueOf → JDBC timezone conversion). Convert to
     * Europe/Madrid wall-clock so the CSV shows the times people expect.
     */
    private static String toMadridWallClock(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(MADRID)
            .toLocalDateTime()
            .format(ISO);
    }

    public static String header() {
        return "id,objectId,neighborhoodName,districtName,serviceType,interruptionDate,repositionDate," +
               "affectedClients,latitude,longitude,sourceUrl,rawResponseHash,firstSeenAt,fetchedAt";
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
            csv(fetchedAt)
        );
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
