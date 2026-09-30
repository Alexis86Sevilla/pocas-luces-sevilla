package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Native JDBC paths for {@link EnelOutage}.
 *
 * <p>Timezone contract (see README, "Timezone contract"): every {@link LocalDateTime} is
 * Europe/Madrid wall-clock and is bound and read as a {@code java.time} value through JDBC
 * 4.2 ({@code setObject}/{@code getObject(..., LocalDateTime.class)}), never through
 * {@code java.sql.Timestamp}, whose conversions go through the JVM default time zone. This
 * keeps the native path and the JPA path (configured with
 * {@code hibernate.type.java_time_use_direct_jdbc=true}) byte-for-byte consistent whatever
 * zone the JVM runs in.</p>
 */
@Repository
public class EnelOutageRepositoryImpl implements EnelOutageRepositoryCustom {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean h2;

    @PersistenceContext
    private EntityManager entityManager;

    public EnelOutageRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate,
                                    @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.h2 = datasourceUrl != null && datasourceUrl.toLowerCase().contains(":h2:");
    }

    @Override
    @Transactional
    public int upsert(EnelOutage outage) {
        if (h2) {
            return upsertH2(outage);
        }
        return upsertPostgreSql(outage);
    }

    private int upsertH2(EnelOutage outage) {
        // H2 does not support ON CONFLICT DO UPDATE in all versions used by tests.
        // For H2 we fall back to a JPA read-then-write within the same transaction.
        // Production PostgreSQL uses the truly atomic ON CONFLICT upsert below.
        EnelOutage existing = findByLocationKey(
            outage.getLatitude(),
            outage.getLongitude(),
            outage.getInterruptionDate(),
            outage.getServiceType());

        if (existing == null) {
            entityManager.persist(outage);
            return 1;
        }

        existing.setObjectId(outage.getObjectId());
        existing.setLatitude(outage.getLatitude());
        existing.setLongitude(outage.getLongitude());
        existing.setAffectedClients(outage.getAffectedClients());
        existing.setRepositionDate(outage.getRepositionDate());
        existing.setNeighborhoodName(outage.getNeighborhoodName());
        existing.setDistrictName(outage.getDistrictName());
        existing.setCause(outage.getCause());
        existing.setSourceUrl(outage.getSourceUrl());
        existing.setRawResponseHash(outage.getRawResponseHash());
        existing.setRawResponse(outage.getRawResponse());
        existing.setFetchedAt(outage.getFetchedAt());
        existing.setUpdatedAt(outage.getUpdatedAt());
        existing.setActive(outage.isActive());
        existing.setResolvedAt(outage.getResolvedAt());
        // Announcement state: eligibility and the announced_at marks are never overwritten by
        // a fetch; only the consecutive-missing counter is reset (the outage is published again).
        existing.setMissingPolls(outage.getMissingPolls());
        entityManager.merge(existing);
        return 1;
    }

    private EnelOutage findByLocationKey(Double latitude, Double longitude, LocalDateTime interruptionDate, String serviceType) {
        var query = entityManager.createQuery(
            """
                SELECT o FROM EnelOutage o
                WHERE o.latitude = :latitude
                AND o.longitude = :longitude
                AND o.interruptionDate = :interruptionDate
                AND o.serviceType = :serviceType
                """, EnelOutage.class);
        query.setParameter("latitude", latitude);
        query.setParameter("longitude", longitude);
        query.setParameter("interruptionDate", interruptionDate);
        query.setParameter("serviceType", serviceType);
        return query.getResultStream().findFirst().orElse(null);
    }

    private int upsertPostgreSql(EnelOutage outage) {
        String sql = """
            INSERT INTO enel_outages (
                object_id, latitude, longitude, affected_clients, service_type,
                interruption_date, reposition_date, neighborhood_name, district_name, cause, source_url,
                raw_response_hash, raw_response, first_seen_at, fetched_at, created_at, updated_at, active, resolved_at,
                announce_eligible, announced_at, restoration_announced_at, missing_polls
            ) VALUES (
                :objectId, :latitude, :longitude, :affectedClients, :serviceType,
                :interruptionDate, :repositionDate, :neighborhoodName, :districtName, :cause, :sourceUrl,
                :rawResponseHash, :rawResponse, :firstSeenAt, :fetchedAt, :createdAt, :updatedAt, :active, :resolvedAt,
                :announceEligible, :announcedAt, :restorationAnnouncedAt, :missingPolls
            )
            ON CONFLICT (latitude, longitude, interruption_date, service_type)
            DO UPDATE SET
                object_id = EXCLUDED.object_id,
                affected_clients = EXCLUDED.affected_clients,
                reposition_date = EXCLUDED.reposition_date,
                neighborhood_name = EXCLUDED.neighborhood_name,
                district_name = EXCLUDED.district_name,
                cause = EXCLUDED.cause,
                source_url = EXCLUDED.source_url,
                raw_response_hash = EXCLUDED.raw_response_hash,
                raw_response = EXCLUDED.raw_response,
                fetched_at = EXCLUDED.fetched_at,
                updated_at = EXCLUDED.updated_at,
                active = EXCLUDED.active,
                resolved_at = EXCLUDED.resolved_at,
                missing_polls = EXCLUDED.missing_polls
            """;
        // Deliberately NOT updated on conflict: first_seen_at, created_at, announce_eligible,
        // announced_at and restoration_announced_at. A row that predates the Telegram alerts
        // must stay ineligible forever, and an announcement mark must never be cleared.
        return jdbcTemplate.update(sql, toParameters(outage));
    }

    private Map<String, Object> toParameters(EnelOutage outage) {
        Map<String, Object> params = new HashMap<>();
        params.put("objectId", outage.getObjectId());
        params.put("latitude", outage.getLatitude());
        params.put("longitude", outage.getLongitude());
        params.put("affectedClients", outage.getAffectedClients());
        params.put("serviceType", outage.getServiceType());
        params.put("interruptionDate", outage.getInterruptionDate());
        params.put("repositionDate", outage.getRepositionDate());
        params.put("neighborhoodName", outage.getNeighborhoodName());
        params.put("districtName", outage.getDistrictName());
        params.put("cause", outage.getCause());
        params.put("sourceUrl", outage.getSourceUrl());
        params.put("rawResponseHash", outage.getRawResponseHash());
        params.put("rawResponse", outage.getRawResponse());
        params.put("firstSeenAt", outage.getFirstSeenAt());
        params.put("fetchedAt", outage.getFetchedAt());
        params.put("createdAt", outage.getCreatedAt());
        params.put("updatedAt", outage.getUpdatedAt());
        params.put("active", outage.isActive());
        params.put("resolvedAt", outage.getResolvedAt());
        params.put("announceEligible", outage.isAnnounceEligible());
        params.put("announcedAt", outage.getAnnouncedAt());
        params.put("restorationAnnouncedAt", outage.getRestorationAnnouncedAt());
        params.put("missingPolls", outage.getMissingPolls());
        return params;
    }

    @Override
    public List<EnelOutage> findCurrentlyActive(LocalDateTime now, LocalDateTime since) {
        String sql = """
            SELECT id, object_id, latitude, longitude, affected_clients, service_type,
                   interruption_date, reposition_date, neighborhood_name, district_name, cause, source_url,
                   raw_response_hash, raw_response, first_seen_at, fetched_at, created_at, updated_at, active, resolved_at,
                   announce_eligible, announced_at, restoration_announced_at, missing_polls
            FROM enel_outages
            WHERE active = true
            AND fetched_at > :since
            AND interruption_date IS NOT NULL
            ORDER BY interruption_date DESC
            """;
        Map<String, Object> params = Map.of("since", since);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToEnelOutage(rs));
    }

    private EnelOutage mapRowToEnelOutage(ResultSet rs) throws SQLException {
        EnelOutage o = new EnelOutage();
        o.setId(rs.getLong("id"));
        o.setObjectId(rs.getString("object_id"));
        o.setLatitude(rs.getObject("latitude") != null ? rs.getDouble("latitude") : null);
        o.setLongitude(rs.getObject("longitude") != null ? rs.getDouble("longitude") : null);
        o.setAffectedClients(rs.getObject("affected_clients") != null ? rs.getInt("affected_clients") : null);
        o.setServiceType(rs.getString("service_type"));
        o.setInterruptionDate(rs.getObject("interruption_date", LocalDateTime.class));
        o.setRepositionDate(rs.getObject("reposition_date", LocalDateTime.class));
        o.setNeighborhoodName(rs.getString("neighborhood_name"));
        o.setDistrictName(rs.getString("district_name"));
        o.setCause(rs.getString("cause"));
        o.setSourceUrl(rs.getString("source_url"));
        o.setRawResponseHash(rs.getString("raw_response_hash"));
        o.setRawResponse(rs.getString("raw_response"));
        o.setFirstSeenAt(rs.getObject("first_seen_at", LocalDateTime.class));
        o.setFetchedAt(rs.getObject("fetched_at", LocalDateTime.class));
        o.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        o.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        o.setActive(rs.getBoolean("active"));
        o.setResolvedAt(rs.getObject("resolved_at", LocalDateTime.class));
        o.setAnnounceEligible(rs.getBoolean("announce_eligible"));
        o.setAnnouncedAt(rs.getObject("announced_at", LocalDateTime.class));
        o.setRestorationAnnouncedAt(rs.getObject("restoration_announced_at", LocalDateTime.class));
        o.setMissingPolls(rs.getInt("missing_polls"));
        return o;
    }

    @Override
    @Transactional
    public void setActiveByObjectIds(Collection<String> objectIds, boolean active) {
        if (objectIds == null || objectIds.isEmpty()) {
            return;
        }
        String sql = "UPDATE enel_outages SET active = :active WHERE object_id IN (:objectIds)";
        Map<String, Object> params = Map.of("active", active, "objectIds", objectIds);
        jdbcTemplate.update(sql, params);
    }

}
