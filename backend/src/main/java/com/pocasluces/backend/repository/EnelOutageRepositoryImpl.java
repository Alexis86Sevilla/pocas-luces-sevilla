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
import java.util.HashMap;
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
        existing.setSourceUrl(outage.getSourceUrl());
        existing.setRawResponseHash(outage.getRawResponseHash());
        existing.setRawResponse(outage.getRawResponse());
        existing.setFetchedAt(outage.getFetchedAt());
        existing.setUpdatedAt(outage.getUpdatedAt());
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
                interruption_date, reposition_date, neighborhood_name, source_url,
                raw_response_hash, raw_response, first_seen_at, fetched_at, created_at, updated_at
            ) VALUES (
                :objectId, :latitude, :longitude, :affectedClients, :serviceType,
                :interruptionDate, :repositionDate, :neighborhoodName, :sourceUrl,
                :rawResponseHash, :rawResponse, :firstSeenAt, :fetchedAt, :createdAt, :updatedAt
            )
            ON CONFLICT (uk_enel_outage_natural_key)
            DO UPDATE SET
                object_id = EXCLUDED.object_id,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                affected_clients = EXCLUDED.affected_clients,
                reposition_date = EXCLUDED.reposition_date,
                source_url = EXCLUDED.source_url,
                raw_response_hash = EXCLUDED.raw_response_hash,
                raw_response = EXCLUDED.raw_response,
                fetched_at = EXCLUDED.fetched_at,
                updated_at = EXCLUDED.updated_at
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
        params.put("sourceUrl", outage.getSourceUrl());
        params.put("rawResponseHash", outage.getRawResponseHash());
        params.put("rawResponse", outage.getRawResponse());
        params.put("firstSeenAt", toTimestamp(outage.getFirstSeenAt()));
        params.put("fetchedAt", toTimestamp(outage.getFetchedAt()));
        params.put("createdAt", toTimestamp(outage.getCreatedAt()));
        params.put("updatedAt", toTimestamp(outage.getUpdatedAt()));
        return params;
    }

    private Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }
}
