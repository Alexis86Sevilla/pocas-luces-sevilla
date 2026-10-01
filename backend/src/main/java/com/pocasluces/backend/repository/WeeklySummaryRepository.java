package com.pocasluces.backend.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Native JDBC queries behind the weekly Telegram summary: aggregated outage statistics and
 * the persisted "week already sent" marks. Same timezone contract as
 * {@link EnelOutageRepositoryImpl}: every {@link LocalDateTime} is Europe/Madrid wall-clock
 * and travels through JDBC 4.2 as a {@code java.time} value.
 */
@Repository
public class WeeklySummaryRepository {

    /** Totals of the outages that started inside one time window. */
    public record Totals(long outages, long affectedClients, long briefOutages) {}

    /** Number of outages that started in a district during the window. */
    public record DistrictCount(String district, long outages) {}

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WeeklySummaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Outages whose {@code interruption_date} is in {@code [from, to)}: count, summed affected
     * supply points (null counts as 0) and how many were brief (same rule as
     * {@code EnelOutage.isBrief()}: resolved and last seen in the same poll as first seen).
     */
    public Totals totals(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT COUNT(*) AS outages,
                   COALESCE(SUM(affected_clients), 0) AS affected_clients,
                   COALESCE(SUM(CASE WHEN resolved_at IS NOT NULL AND fetched_at = first_seen_at THEN 1 ELSE 0 END), 0) AS brief_outages
            FROM enel_outages
            WHERE interruption_date >= :from AND interruption_date < :to
            """;
        Totals totals = jdbcTemplate.queryForObject(sql, Map.of("from", from, "to", to),
            (rs, rowNum) -> new Totals(rs.getLong("outages"), rs.getLong("affected_clients"), rs.getLong("brief_outages")));
        return totals == null ? new Totals(0, 0, 0) : totals;
    }

    /**
     * Outage counts per district for the window, most outages first then alphabetical by the
     * database collation (callers re-sort if they need a locale-aware tie order). Rows with no
     * district are left out; the unidentified-zone placeholder is filtered by the caller.
     */
    public List<DistrictCount> districtCounts(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT district_name, COUNT(*) AS outages
            FROM enel_outages
            WHERE interruption_date >= :from AND interruption_date < :to
            AND district_name IS NOT NULL AND district_name <> ''
            GROUP BY district_name
            ORDER BY COUNT(*) DESC, district_name ASC
            """;
        return jdbcTemplate.query(sql, Map.of("from", from, "to", to),
            (rs, rowNum) -> new DistrictCount(rs.getString("district_name"), rs.getLong("outages")));
    }

    /** Earliest moment we ever saw any outage, or null when the table is empty. */
    public LocalDateTime earliestFirstSeen() {
        return jdbcTemplate.queryForObject("SELECT MIN(first_seen_at) FROM enel_outages", Map.of(),
            (rs, rowNum) -> rs.getObject(1, LocalDateTime.class));
    }

    public boolean isWeekSent(LocalDate weekStart) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telegram_weekly_summary WHERE week_start = :weekStart",
            Map.of("weekStart", weekStart), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Records the week as sent; a no-op when it is already recorded.
     *
     * @return true when this call inserted the row
     */
    public boolean markWeekSent(LocalDate weekStart, LocalDateTime sentAt) {
        return jdbcTemplate.update("""
            INSERT INTO telegram_weekly_summary (week_start, sent_at) VALUES (:weekStart, :sentAt)
            ON CONFLICT (week_start) DO NOTHING
            """, Map.of("weekStart", weekStart, "sentAt", sentAt)) > 0;
    }
}
