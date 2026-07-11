package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.OutageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutageEventRepository extends JpaRepository<OutageEvent, Long> {

    @Query("SELECT o FROM OutageEvent o JOIN FETCH o.neighborhood")
    List<OutageEvent> findAllWithNeighborhood();

    @Query("SELECT o FROM OutageEvent o JOIN FETCH o.neighborhood WHERE YEAR(o.date) = :year")
    List<OutageEvent> findByYear(@Param("year") int year);

    @Query("SELECT o FROM OutageEvent o JOIN FETCH o.neighborhood WHERE YEAR(o.date) = :year AND MONTH(o.date) = :month")
    List<OutageEvent> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("""
        SELECT n.id, n.name, n.category, COUNT(o), AVG(o.durationMinutes)
        FROM OutageEvent o JOIN o.neighborhood n
        WHERE YEAR(o.date) = :year
        GROUP BY n.id, n.name, n.category
        """)
    List<Object[]> findStatsByYear(@Param("year") int year);
}
