package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.OutageEvent;

import java.time.LocalDate;

public record OutageEventResponse(
    Long id,
    LocalDate date,
    int durationMinutes,
    String source,
    NeighborhoodResponse neighborhood
) {

    public static OutageEventResponse from(OutageEvent event) {
        if (event == null) {
            return null;
        }
        return new OutageEventResponse(
            event.getId(),
            event.getDate(),
            event.getDurationMinutes(),
            event.getSource(),
            NeighborhoodResponse.from(event.getNeighborhood())
        );
    }
}
