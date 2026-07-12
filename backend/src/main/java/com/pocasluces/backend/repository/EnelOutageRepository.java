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

    @Modifying
    @Transactional
    @Query("UPDATE EnelOutage o SET o.active = false")
    void setAllInactive();

    @Modifying
    @Transactional
    @Query("UPDATE EnelOutage o SET o.active = :active WHERE o.objectId IN :objectIds")
    void setActiveByObjectIds(@Param("objectIds") Collection<String> objectIds, @Param("active") boolean active);

    List<EnelOutage> findAllByOrderByInterruptionDateDesc();

    Optional<EnelOutage> findByObjectId(String objectId);

    Optional<EnelOutage> findByNeighborhoodNameAndInterruptionDateAndServiceType(
            String neighborhoodName,
            LocalDateTime interruptionDate,
            String serviceType);

    Page<EnelOutage> findByNeighborhoodNameIgnoreCase(String neighborhoodName, Pageable pageable);

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
