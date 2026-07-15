package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        EnelOutage existing = findByNaturalKey(
            outage.getNeighborhoodName(),
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
        existing.setSourceUrl(outage.getSourceUrl());
        existing.setRawResponseHash(outage.getRawResponseHash());
        existing.setRawResponse(outage.getRawResponse());
        existing.setFetchedAt(outage.getFetchedAt());
        existing.setUpdatedAt(outage.getUpdatedAt());
        existing.setActive(outage.isActive());
        entityManager.merge(existing);
        return 1;
    }

    private EnelOutage findByNaturalKey(String neighborhoodName, LocalDateTime interruptionDate, String serviceType) {
        var query = entityManager.createQuery(
            """
                SELECT o FROM EnelOutage o
                WHERE o.neighborhoodName = :neighborhoodName
                AND o.interruptionDate = :interruptionDate
                AND o.serviceType = :serviceType
                """, EnelOutage.class);
        query.setParameter("neighborhoodName", neighborhoodName);
        query.setParameter("interruptionDate", interruptionDate);
        query.setParameter("serviceType", serviceType);
        return query.getResultStream().findFirst().orElse(null);
    }

    private int upsertPostgreSql(EnelOutage outage) {
        String sql = """
            INSERT INTO enel_outages (
                object_id, latitude, longitude, affected_clients, service_type,
                interruption_date, reposition_date, neighborhood_name, district_name, source_url,
                raw_response_hash, raw_response, first_seen_at, fetched_at, created_at, updated_at, active
            ) VALUES (
                :objectId, :latitude, :longitude, :affectedClients, :serviceType,
                :interruptionDate, :repositionDate, :neighborhoodName, :districtName, :sourceUrl,
                :rawResponseHash, :rawResponse, :firstSeenAt, :fetchedAt, :createdAt, :updatedAt, :active
            )
            ON CONFLICT (neighborhood_name, interruption_date, service_type)
            DO UPDATE SET
                object_id = EXCLUDED.object_id,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                affected_clients = EXCLUDED.affected_clients,
                reposition_date = EXCLUDED.reposition_date,
                neighborhood_name = EXCLUDED.neighborhood_name,
                district_name = EXCLUDED.district_name,
                source_url = EXCLUDED.source_url,
                raw_response_hash = EXCLUDED.raw_response_hash,
                raw_response = EXCLUDED.raw_response,
                fetched_at = EXCLUDED.fetched_at,
                updated_at = EXCLUDED.updated_at,
                active = EXCLUDED.active
            """;
        return jdbcTemplate.update(sql, toParameters(outage));
    }

    private Map<String, Object> toParameters(EnelOutage outage) {
        Map<String, Object> params = new HashMap<>();
        params.put("objectId", outage.getObjectId());
        params.put("latitude", outage.getLatitude());
        params.put("longitude", outage.getLongitude());
        params.put("affectedClients", outage.getAffectedClients());
        params.put("serviceType", outage.getServiceType());
        params.put("interruptionDate", toTimestamp(outage.getInterruptionDate()));
        params.put("repositionDate", toTimestamp(outage.getRepositionDate()));
        params.put("neighborhoodName", outage.getNeighborhoodName());
        params.put("districtName", outage.getDistrictName());
        params.put("sourceUrl", outage.getSourceUrl());
        params.put("rawResponseHash", outage.getRawResponseHash());
        params.put("rawResponse", outage.getRawResponse());
        params.put("firstSeenAt", toTimestamp(outage.getFirstSeenAt()));
        params.put("fetchedAt", toTimestamp(outage.getFetchedAt()));
        params.put("createdAt", toTimestamp(outage.getCreatedAt()));
        params.put("updatedAt", toTimestamp(outage.getUpdatedAt()));
        params.put("active", outage.isActive());
        return params;
    }

    @Override
    public List<EnelOutage> findCurrentlyActive(LocalDateTime now, LocalDateTime since) {
        String sql = """
            SELECT id, object_id, latitude, longitude, affected_clients, service_type,
                   interruption_date, reposition_date, neighborhood_name, district_name, source_url,
                   raw_response_hash, raw_response, first_seen_at, fetched_at, created_at, updated_at, active
            FROM enel_outages
            WHERE active = true
            AND fetched_at > :since
            AND interruption_date IS NOT NULL
            ORDER BY interruption_date DESC
            """;
        Map<String, Object> params = Map.of("since", since);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToEnelOutage(rs));
    }

    private EnelOutage mapRowToEnelOutage(java.sql.ResultSet rs) throws java.sql.SQLException {
        EnelOutage o = new EnelOutage();
        o.setId(rs.getLong("id"));
        o.setObjectId(rs.getString("object_id"));
        o.setLatitude(rs.getObject("latitude") != null ? rs.getDouble("latitude") : null);
        o.setLongitude(rs.getObject("longitude") != null ? rs.getDouble("longitude") : null);
        o.setAffectedClients(rs.getObject("affected_clients") != null ? rs.getInt("affected_clients") : null);
        o.setServiceType(rs.getString("service_type"));
        o.setInterruptionDate(toLocalDateTime(rs.getTimestamp("interruption_date")));
        o.setRepositionDate(toLocalDateTime(rs.getTimestamp("reposition_date")));
        o.setNeighborhoodName(rs.getString("neighborhood_name"));
        o.setDistrictName(rs.getString("district_name"));
        o.setSourceUrl(rs.getString("source_url"));
        o.setRawResponseHash(rs.getString("raw_response_hash"));
        o.setRawResponse(rs.getString("raw_response"));
        o.setFirstSeenAt(toLocalDateTime(rs.getTimestamp("first_seen_at")));
        o.setFetchedAt(toLocalDateTime(rs.getTimestamp("fetched_at")));
        o.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        o.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        o.setActive(rs.getBoolean("active"));
        return o;
    }

    @Override
    @Transactional
    public void setAllInactive() {
        jdbcTemplate.update("UPDATE enel_outages SET active = false", Collections.emptyMap());
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }
}
