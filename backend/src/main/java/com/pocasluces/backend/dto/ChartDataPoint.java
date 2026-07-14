package com.pocasluces.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChartDataPoint {
    private int month;
    private String districtName;
    private long count;
}
