package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;

import java.time.LocalDateTime;
import java.util.List;

public interface EnelOutageRepositoryCustom {

    /**
     * Atomically inserts a new outage or updates the existing row matched by the
     * natural key (neighborhood_name, interruption_date, service_type).
     *
     * <p>On update, first_seen_at and created_at are preserved from the existing row;
     * all other mutable columns are overwritten with the provided values.</p>
     *
     * @return the number of rows affected (1)
     */
    int upsert(EnelOutage outage);

    /**
     * Returns outages that are still active: reposition is in the future (or null),
     * and the record was fetched recently enough to be trusted.
     */
    List<EnelOutage> findCurrentlyActive(LocalDateTime now, LocalDateTime since);
}
