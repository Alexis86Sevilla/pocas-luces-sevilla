package com.pocasluces.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Maps (latitude, longitude) to Sevilla neighborhoods.
 * Approximate center points — Sevilla is ~10km across.
 * Within 1.2km of center → that neighborhood.
 */
@Slf4j
@Service
public class NeighborhoodLocator {

    private record Zone(String name, String category, double lat, double lon) {}

    private static final List<Zone> ZONES = List.of(
        // ── Barrios humildes ──
        new Zone("San Pablo",        "humilde",   37.3970, -5.9800),
        new Zone("Polígono Sur",     "humilde",   37.3630, -5.9750),
        new Zone("Torreblanca",      "humilde",   37.3750, -5.9150),
        new Zone("Los Pajaritos",    "humilde",   37.3700, -5.9550),
        new Zone("Palmete",          "humilde",   37.3800, -5.9400),
        new Zone("Cerro del Águila", "humilde",   37.3720, -5.9700),
        new Zone("Pino Montano",     "humilde",   37.4400, -5.9450),
        new Zone("Alcosa",           "humilde",   37.3950, -5.9200),

        // ── Clase media ──
        new Zone("Triana",           "medio",     37.3830, -6.0050),
        new Zone("La Macarena",      "medio",     37.4150, -5.9850),
        new Zone("Nervión",          "medio",     37.3850, -5.9750),
        new Zone("San Bernardo",     "medio",     37.3820, -5.9850),
        new Zone("Bellavista",       "medio",     37.3400, -5.9760),
        new Zone("San Jerónimo",     "medio",     37.4350, -5.9600),
        new Zone("Heliópolis",       "medio",     37.3650, -5.9850),

        // ── Acomodados ──
        new Zone("Los Remedios",     "acomodado", 37.3750, -6.0050),
        new Zone("Santa Cruz",       "acomodado", 37.3850, -5.9900)
    );

    /**
     * Find the closest neighborhood to given coordinates.
     * Returns null if no neighborhood is within 2km.
     */
    public String findNeighborhood(double lat, double lon) {
        Zone closest = null;
        double minDist = Double.MAX_VALUE;

        for (Zone zone : ZONES) {
            double dist = distance(lat, lon, zone.lat, zone.lon);
            if (dist < minDist) {
                minDist = dist;
                closest = zone;
            }
        }

        // 2km threshold — if too far, it's outside our mapped neighborhoods
        if (closest != null && minDist <= 2.0) {
            log.debug("Coords ({},{}) → {} ({}m)", lat, lon, closest.name, Math.round(minDist * 1000));
            return closest.name;
        }

        log.debug("Coords ({},{}) → no match (closest: {} at {}m)", lat, lon,
            closest != null ? closest.name : "none", Math.round(minDist * 1000));
        return null;
    }

    /** Haversine distance in km */
    private double distance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
