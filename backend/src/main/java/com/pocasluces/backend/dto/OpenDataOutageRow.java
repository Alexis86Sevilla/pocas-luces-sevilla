package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.EnelOutage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One row of the public open-data CSV. Column names are a stable public contract
 * (documented in the README and the methodology page): do not rename or reorder.
 * Raw feed payloads, hashes and internal ids are deliberately not exported.
 * All datetimes are Europe/Madrid wall-clock time, formatted without offset.
 */
public final class OpenDataOutageRow {

    /** Output flavour: standard CSV, or a Spanish-locale Excel friendly variant. */
    public enum Variant {
        /** Comma-delimited, dot decimals, ISO-8601 datetimes with 'T'. */
        STANDARD(',', '.', DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
        /** Semicolon-delimited, decimal comma, datetimes with a space instead of 'T'. */
        EXCEL(';', ',', DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        private final char delimiter;
        private final char decimalSeparator;
        private final DateTimeFormatter dateTime;

        Variant(char delimiter, char decimalSeparator, DateTimeFormatter dateTime) {
            this.delimiter = delimiter;
            this.decimalSeparator = decimalSeparator;
            this.dateTime = dateTime;
        }
    }

    private static final String[] COLUMNS = {
        "interruption_start", "estimated_restoration", "observed_end", "observed_duration_min",
        "affected_supply_points", "category", "cause", "service_type", "district",
        "neighborhood_approx", "latitude", "longitude", "first_seen", "last_seen", "active", "brief"
    };

    private OpenDataOutageRow() {
    }

    public static String header() {
        return header(Variant.STANDARD);
    }

    public static String header(Variant variant) {
        return String.join(String.valueOf(variant.delimiter), COLUMNS);
    }

    public static String toCsvRow(EnelOutage o) {
        return toCsvRow(o, Variant.STANDARD);
    }

    public static String toCsvRow(EnelOutage o, Variant variant) {
        return CsvWriter.rowWith(variant.delimiter, variant.decimalSeparator,
            wallClock(o.getInterruptionDate(), variant),
            wallClock(o.getRepositionDate(), variant),
            wallClock(o.getResolvedAt(), variant),
            observedDurationMinutes(o.getInterruptionDate(), o.getResolvedAt()),
            o.getAffectedClients(),
            category(o.getCause(), o.getServiceType()),
            o.getCause(),
            o.getServiceType(),
            o.getDistrictName(),
            o.getNeighborhoodName(),
            o.getLatitude(),
            o.getLongitude(),
            wallClock(o.getFirstSeenAt(), variant),
            wallClock(o.getFetchedAt(), variant),
            o.isActive(),
            o.isBrief()
        );
    }

    /** Whole minutes between start and observed end; null unless the end is strictly after the start. */
    static Long observedDurationMinutes(LocalDateTime start, LocalDateTime observedEnd) {
        if (start == null || observedEnd == null || !observedEnd.isAfter(start)) {
            return null;
        }
        return Duration.between(start, observedEnd).toMinutes();
    }

    /** Mirrors the frontend outageCategory(): the distributor's cause first, service type LV as fallback. */
    static String category(String cause, String serviceType) {
        if ("Trabajos programados".equals(cause)) {
            return "Programado";
        }
        if ("Avería".equals(cause)) {
            return "Avería";
        }
        return "LV".equals(serviceType) ? "Programado" : "Avería";
    }

    private static String wallClock(LocalDateTime value, Variant variant) {
        return value == null ? null : value.format(variant.dateTime);
    }
}
