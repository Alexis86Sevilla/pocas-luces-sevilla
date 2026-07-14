package com.pocasluces.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DistrictStatsDto {
    private String districtName;
    private long outageCount;
    private double averageMinutes;
}
