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
    String cause
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
            o.getCause()
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
               "affectedClients,latitude,longitude,sourceUrl,rawResponseHash,firstSeenAt,fetchedAt,cause";
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
            csv(cause)
        );
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            // Numeric columns can't start with a formula-injection character; skip sanitization.
            return value.toString();
        }
        String text = neutralizeFormulaInjection(value.toString());
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /**
     * OWASP CSV/formula injection mitigation: if a value starts with a character that
     * spreadsheet software interprets as the start of a formula (=, +, -, @) or with a
     * tab/carriage return, prefix it with a single quote so it is opened as plain text.
     */
    private static String neutralizeFormulaInjection(String text) {
        if (text.isEmpty()) {
            return text;
        }
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + text;
        }
        return text;
    }
}
