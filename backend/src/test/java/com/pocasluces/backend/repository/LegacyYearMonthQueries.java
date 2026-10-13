package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Test-only copy of the read queries exactly as they stood before the 2026-10-02 query
 * hygiene change ({@code YEAR()} / {@code MONTH()} predicates, full entity rows): the
 * reference the new half-open-range, projected queries are compared against in
 * {@link AbstractQueryEquivalenceTest}. Not part of the application.
 */
interface LegacyYearMonthQueries extends Repository<EnelOutage, Long> {

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

    Page<EnelOutage> findByNeighborhoodNameIgnoreCase(String neighborhoodName, Pageable pageable);
}
