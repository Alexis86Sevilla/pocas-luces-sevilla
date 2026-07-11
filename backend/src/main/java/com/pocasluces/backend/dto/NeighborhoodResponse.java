package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.Neighborhood;

public record NeighborhoodResponse(
    Long id,
    String name,
    String postalCode,
    String category
) {

    public static NeighborhoodResponse from(Neighborhood neighborhood) {
        if (neighborhood == null) {
            return null;
        }
        return new NeighborhoodResponse(
            neighborhood.getId(),
            neighborhood.getName(),
            neighborhood.getPostalCode(),
            neighborhood.getCategory()
        );
    }
}
