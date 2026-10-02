package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import java.util.Collection;
import org.springframework.transaction.annotation.Transactional;

public interface EnelOutageRepository extends JpaRepository<EnelOutage, Long>, EnelOutageRepositoryCustom {

    /**
     * Marks as resolved every outage that is still flagged active but whose fetchedAt
     * predates {@code now}, i.e. it was not touched by the current fetch run (the run's
     * own upserts set fetchedAt = now, so they are excluded by the strict {@code <}).
     * A reappearing outage is re-opened by the ordinary upsert (which clears resolvedAt),
     * not by this method.
     *
     * @return the number of rows resolved
     */
    @Modifying
    @Transactional
    // resolvedAt = last poll in which Endesa still published the outage: a conservative
    // lower bound that never inflates durations, even after gaps in our own polling.
    @Query("UPDATE EnelOutage o SET o.active = false, o.resolvedAt = o.fetchedAt " +
           "WHERE o.active = true AND o.fetchedAt < :now")
    int resolveStaleActiveOutages(@Param("now") LocalDateTime now);

    /** Rows currently flagged active; read by the scheduler before its upserts (mass-resolution guard). */
    long countByActiveTrue();

    /**
     * Number of rows {@link #resolveStaleActiveOutages} would resolve at {@code now}: still
     * active and not re-upserted by the current run. Same predicate as the resolve step, and a
     * JPQL query like it, so the H2 (JPA) upsert path is flushed before counting.
     */
    @Query("SELECT COUNT(o) FROM EnelOutage o WHERE o.active = true AND o.fetchedAt < :now")
    long countActiveNotFetchedAt(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE EnelOutage o SET o.active = :active WHERE o.objectId IN :objectIds")
    void setActiveByObjectIds(@Param("objectIds") Collection<String> objectIds, @Param("active") boolean active);

    // ---- Telegram announcement state (see OutageAnnouncer) -------------------------------

    /**
     * Counts one more consecutive "missing" poll for every announced outage that the
     * current run did not see (it is inactive after the resolve step) and whose restoration
     * has not been announced yet. Must run inside the scheduler's transaction, right after
     * {@link #resolveStaleActiveOutages}. Reappearing outages are reset to 0 by the upsert.
     * The set is tiny (announced but not yet restoration-announced), so the update is cheap.
     */
    @Modifying
    @Transactional
    @Query("UPDATE EnelOutage o SET o.missingPolls = o.missingPolls + 1 " +
           "WHERE o.active = false AND o.announcedAt IS NOT NULL AND o.restorationAnnouncedAt IS NULL")
    int incrementMissingPollsOfAnnouncedInactiveOutages();

    /**
     * Outages to announce as new: eligible, never announced, published in the current poll
     * ({@code active}) and in at least one earlier successful poll ({@code fetchedAt >
     * firstSeenAt}: the upsert advances {@code fetchedAt} on every poll and never touches
     * {@code firstSeenAt}), already started, and started no earlier than
     * {@code oldestStart} (stale backlog is never announced).
     */
    @Query("""
        SELECT o FROM EnelOutage o
        WHERE o.announceEligible = true
        AND o.announcedAt IS NULL
        AND o.active = true
        AND o.fetchedAt > o.firstSeenAt
        AND o.interruptionDate <= :now
        AND o.interruptionDate >= :oldestStart
        ORDER BY o.interruptionDate ASC, o.id ASC
        """)
    List<EnelOutage> findNewOutagesToAnnounce(@Param("now") LocalDateTime now,
                                              @Param("oldestStart") LocalDateTime oldestStart);

    /**
     * Outages to announce as restored: announced as new, not yet announced as restored,
     * inactive, and missing from at least {@code minMissingPolls} consecutive successful polls.
     */
    @Query("""
        SELECT o FROM EnelOutage o
        WHERE o.announcedAt IS NOT NULL
        AND o.restorationAnnouncedAt IS NULL
        AND o.active = false
        AND o.missingPolls >= :minMissingPolls
        ORDER BY o.interruptionDate ASC, o.id ASC
        """)
    List<EnelOutage> findRestoredOutagesToAnnounce(@Param("minMissingPolls") int minMissingPolls);

    @Modifying
    @Transactional
    @Query("UPDATE EnelOutage o SET o.announcedAt = :at WHERE o.id IN :ids AND o.announcedAt IS NULL")
    int markAnnounced(@Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at);

    @Modifying
    @Transactional
    @Query("UPDATE EnelOutage o SET o.restorationAnnouncedAt = :at " +
           "WHERE o.id IN :ids AND o.restorationAnnouncedAt IS NULL")
    int markRestorationAnnounced(@Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at);

    List<EnelOutage> findAllByOrderByInterruptionDateDesc();

    Optional<EnelOutage> findByObjectId(String objectId);

    Optional<EnelOutage> findByLatitudeAndLongitudeAndInterruptionDateAndServiceType(
            Double latitude,
            Double longitude,
            LocalDateTime interruptionDate,
            String serviceType);

    Page<EnelOutage> findByNeighborhoodNameIgnoreCase(String neighborhoodName, Pageable pageable);

    long countByDistrictNameIsNull();

    @Query("""
        SELECT o FROM EnelOutage o
        WHERE o.districtName IS NULL AND o.id > :afterId
        ORDER BY o.id ASC
        """)
    List<EnelOutage> findTopBatchWithNullDistrict(@Param("afterId") long afterId, Pageable pageable);

    // Monthly aggregation for charts: count outages by month and district.
    @Query("""
        SELECT MONTH(o.interruptionDate), o.districtName, COUNT(o)
        FROM EnelOutage o
        WHERE YEAR(o.interruptionDate) = :year
        AND o.districtName IS NOT NULL
        GROUP BY MONTH(o.interruptionDate), o.districtName
        ORDER BY MONTH(o.interruptionDate), o.districtName
        """)
    List<Object[]> aggregateByMonthAndDistrict(@Param("year") int year);

    @Query("SELECT o FROM EnelOutage o WHERE YEAR(o.interruptionDate) = :year ORDER BY o.interruptionDate DESC")
    List<EnelOutage> findByYear(@Param("year") int year);

    @Query("SELECT o FROM EnelOutage o WHERE YEAR(o.interruptionDate) = :year")
    Page<EnelOutage> findByYear(@Param("year") int year, Pageable pageable);

    @Query("""
        SELECT o FROM EnelOutage o
        WHERE YEAR(o.interruptionDate) = :year
        AND MONTH(o.interruptionDate) = :month
        ORDER BY o.interruptionDate DESC
        """)
    List<EnelOutage> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT o FROM EnelOutage o WHERE YEAR(o.interruptionDate) = :year AND MONTH(o.interruptionDate) = :month")
    Page<EnelOutage> findByYearAndMonth(@Param("year") int year, @Param("month") int month, Pageable pageable);
}
