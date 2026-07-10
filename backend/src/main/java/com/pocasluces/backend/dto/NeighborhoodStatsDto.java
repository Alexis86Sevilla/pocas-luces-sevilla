package com.pocasluces.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NeighborhoodStatsDto {
    private Long neighborhoodId;
    private String neighborhoodName;
    private long outageCount;
    private double averageMinutes;
    private String category;
}
