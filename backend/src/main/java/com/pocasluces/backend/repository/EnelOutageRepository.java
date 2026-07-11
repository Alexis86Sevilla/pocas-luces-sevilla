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

public interface EnelOutageRepository extends JpaRepository<EnelOutage, Long> {

    List<EnelOutage> findAllByOrderByInterruptionDateDesc();

    Optional<EnelOutage> findByObjectId(Long objectId);

    Page<EnelOutage> findByNeighborhoodNameIgnoreCase(String neighborhoodName, Pageable pageable);

    // Currently active outages: no reposition date yet, or reposition is in the future,
    // and we fetched it recently enough to trust it is still ongoing.
    @Query("""
        SELECT o FROM EnelOutage o
        WHERE (o.repositionDate IS NULL OR o.repositionDate > :now)
        AND o.fetchedAt > :since
        AND o.interruptionDate IS NOT NULL
        ORDER BY o.interruptionDate DESC
        """)
    List<EnelOutage> findCurrentlyActive(@Param("now") LocalDateTime now, @Param("since") LocalDateTime since);

    // Monthly aggregation for charts: count outages by month and neighborhood.
    @Query("""
        SELECT MONTH(o.interruptionDate), o.neighborhoodName, COUNT(o)
        FROM EnelOutage o
        WHERE YEAR(o.interruptionDate) = :year
        AND o.neighborhoodName IS NOT NULL
        GROUP BY MONTH(o.interruptionDate), o.neighborhoodName
        ORDER BY MONTH(o.interruptionDate), o.neighborhoodName
        """)
    List<Object[]> aggregateByMonthAndNeighborhood(@Param("year") int year);

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
