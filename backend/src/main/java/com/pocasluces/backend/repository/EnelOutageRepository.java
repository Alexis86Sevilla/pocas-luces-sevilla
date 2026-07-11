package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnelOutageRepository extends JpaRepository<EnelOutage, Long> {

    List<EnelOutage> findAllByOrderByInterruptionDateDesc();

    // Cortes actualmente activos: reposición estimada futura y reportados recientemente
    @Query("""
        SELECT o FROM EnelOutage o
        WHERE o.repositionDate > CURRENT_TIMESTAMP
        AND o.fetchedAt > CURRENT_TIMESTAMP - INTERVAL '6' HOUR
        ORDER BY o.affectedClients DESC, o.interruptionDate DESC
    """)
    List<EnelOutage> findCurrentlyActive();

    // Agregado mensual para el chart: cuenta de cortes por mes y barrio
    @Query("""
        SELECT EXTRACT(MONTH FROM o.interruptionDate), o.neighborhoodName, COUNT(o)
        FROM EnelOutage o
        WHERE EXTRACT(YEAR FROM o.interruptionDate) = :year
        AND o.neighborhoodName IS NOT NULL
        GROUP BY EXTRACT(MONTH FROM o.interruptionDate), o.neighborhoodName
        ORDER BY EXTRACT(MONTH FROM o.interruptionDate)
    """)
    List<Object[]> aggregateByMonthAndNeighborhood(@Param("year") int year);

    // Cortes de un mes concreto
    @Query("""
        SELECT o FROM EnelOutage o
        WHERE EXTRACT(YEAR FROM o.interruptionDate) = :year
        AND EXTRACT(MONTH FROM o.interruptionDate) = :month
        ORDER BY o.interruptionDate DESC
    """)
    List<EnelOutage> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT o FROM EnelOutage o WHERE EXTRACT(YEAR FROM o.interruptionDate) = :year ORDER BY o.interruptionDate DESC")
    List<EnelOutage> findByYear(@Param("year") int year);

    Optional<EnelOutage> findByNeighborhoodNameAndInterruptionDateAndServiceType(
            String neighborhoodName,
            LocalDateTime interruptionDate,
            String serviceType);
}
