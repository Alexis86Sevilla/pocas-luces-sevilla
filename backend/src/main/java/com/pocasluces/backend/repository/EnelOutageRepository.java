package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnelOutageRepository extends JpaRepository<EnelOutage, Long> {

    List<EnelOutage> findAllByOrderByInterruptionDateDesc();

    // Agregado mensual para el chart: cuenta de cortes por mes y barrio
    @Query("""
        SELECT FUNCTION('MONTH', o.interruptionDate), o.neighborhoodName, COUNT(o)
        FROM EnelOutage o
        WHERE FUNCTION('YEAR', o.interruptionDate) = :year
        AND o.neighborhoodName IS NOT NULL
        GROUP BY FUNCTION('MONTH', o.interruptionDate), o.neighborhoodName
        ORDER BY FUNCTION('MONTH', o.interruptionDate)
    """)
    List<Object[]> aggregateByMonthAndNeighborhood(@Param("year") int year);

    // Cortes de un mes concreto
    @Query("""
        SELECT o FROM EnelOutage o
        WHERE FUNCTION('YEAR', o.interruptionDate) = :year
        AND FUNCTION('MONTH', o.interruptionDate) = :month
        ORDER BY o.interruptionDate DESC
    """)
    List<EnelOutage> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT o FROM EnelOutage o WHERE FUNCTION('YEAR', o.interruptionDate) = :year ORDER BY o.interruptionDate DESC")
    List<EnelOutage> findByYear(@Param("year") int year);
}
