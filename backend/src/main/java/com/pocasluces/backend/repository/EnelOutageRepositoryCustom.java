package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EnelOutageRepositoryCustom {

    /**
     * Atomically inserts a new outage or updates the existing row matched by its
     * location key (latitude, longitude, interruption_date, service_type). Coordinates
     * are used instead of neighborhood_name because the neighborhood is itself derived
     * from these same coordinates, and instead of Endesa's objectId because it is not
     * stable across feed layer republishes.
     *
     * <p>On update, first_seen_at and created_at are preserved from the existing row;
     * all other mutable columns are overwritten with the provided values.</p>
     *
     * @return the number of rows affected (1)
     */
    int upsert(EnelOutage outage);

    /**
     * Returns outages that are still active: reposition is in the future (or null),
     * the record was fetched recently enough to be trusted, and the active flag is true.
     */
    List<EnelOutage> findCurrentlyActive(LocalDateTime now, LocalDateTime since);

    /**
     * Sets the active flag for all rows whose objectId is in the given collection.
     * Does nothing when the collection is empty.
     */
    void setActiveByObjectIds(Collection<String> objectIds, boolean active);
}
