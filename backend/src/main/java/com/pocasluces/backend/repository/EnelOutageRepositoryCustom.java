package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;

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
}
