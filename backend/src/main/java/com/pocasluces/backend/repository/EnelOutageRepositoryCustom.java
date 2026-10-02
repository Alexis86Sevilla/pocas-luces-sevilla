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

    /**
     * Every row currently flagged active, read through plain JDBC so the returned objects are
     * detached snapshots: the scheduler compares them with the poll being processed (see
     * start-time corrections) and never flushes them back.
     */
    List<EnelOutage> findAllActive();

    /**
     * Rewrites the start time of one existing row, i.e. moves it to a new location key, when a
     * poll republished the same physical outage with a corrected start. Keeps the original
     * start in {@code original_interruption_date} (first correction only) and stamps
     * {@code start_corrected_at}. The caller must have checked that no other row owns the new
     * key; the ordinary {@link #upsert} with the corrected key then updates the row as a normal
     * re-sighting (same id, {@code first_seen_at} and announcement state).
     *
     * @return the number of rows updated (1, or 0 if the id no longer exists)
     */
    int correctInterruptionDate(long id, LocalDateTime correctedStart, LocalDateTime now);
}
