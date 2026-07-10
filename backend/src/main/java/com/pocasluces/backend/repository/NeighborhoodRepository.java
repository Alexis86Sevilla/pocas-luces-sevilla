package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.Neighborhood;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeighborhoodRepository extends JpaRepository<Neighborhood, Long> {
}
