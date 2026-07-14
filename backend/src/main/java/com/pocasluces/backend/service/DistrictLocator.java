package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves a WGS84 (lat, lon) coordinate to one of Sevilla's 11 official districts.
 *
 * <p>At startup the district boundaries are loaded from {@code geojson/distritos-sevilla.json}
 * and cached as JTS geometries. The lookup follows a three-tier fallback chain:</p>
 *
 * <ol>
 *   <li>Point-in-polygon against the cached district geometries.</li>
 *   <li>If no polygon matches, map the closest neighborhood to its district.</li>
 *   <li>If nothing resolves, return {@code "Zona no identificada"}.</li>
 * </ol>
 */
@Slf4j
@Service
public class DistrictLocator {

    static final String UNKNOWN_DISTRICT = "Zona no identificada";
    private static final String DEFAULT_RESOURCE_PATH = "geojson/distritos-sevilla.json";

    private static final Set<String> OFFICIAL_DISTRICTS = Set.of(
        "Casco Antiguo",
        "Triana",
        "Macarena",
        "Nervión",
        "San Pablo-Santa Justa",
        "Distrito Norte",
        "Bellavista-La Palmera",
        "Sur",
        "Los Remedios",
        "Este-Alcosa-Torreblanca",
        "Cerro-Amate"
    );

    /**
     * Static mapping from neighborhood name (as resolved by {@link NeighborhoodLocator})
     * to the official district it belongs to. Used only as step-2 fallback.
     */
    private static final Map<String, String> NEIGHBORHOOD_TO_DISTRICT = Map.ofEntries(
        Map.entry("Triana", "Triana"),
        Map.entry("La Macarena", "Macarena"),
        Map.entry("Nervión", "Nervión"),
        Map.entry("San Bernardo", "Nervión"),
        Map.entry("Bellavista", "Bellavista-La Palmera"),
        Map.entry("San Jerónimo", "Distrito Norte"),
        Map.entry("Heliópolis", "Bellavista-La Palmera"),
        Map.entry("Los Remedios", "Los Remedios"),
        Map.entry("Santa Cruz", "Casco Antiguo"),
        Map.entry("San Pablo", "San Pablo-Santa Justa"),
        Map.entry("Polígono Sur", "Sur"),
        Map.entry("Torreblanca", "Este-Alcosa-Torreblanca"),
        Map.entry("Los Pajaritos", "Cerro-Amate"),
        Map.entry("Palmete", "Cerro-Amate"),
        Map.entry("Cerro del Águila", "Cerro-Amate"),
        Map.entry("Pino Montano", "Distrito Norte"),
        Map.entry("Alcosa", "Este-Alcosa-Torreblanca"),
        Map.entry("Amate", "Cerro-Amate")
    );

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NeighborhoodLocator neighborhoodLocator;

    private List<District> districts = Collections.emptyList();

    public DistrictLocator(NeighborhoodLocator neighborhoodLocator) {
        this.neighborhoodLocator = Objects.requireNonNull(neighborhoodLocator,
            "NeighborhoodLocator is required");
    }

    @PostConstruct
    void loadDistricts() {
        loadDistricts(DEFAULT_RESOURCE_PATH);
    }

    void loadDistricts(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("District GeoJSON resource not found: " + resourcePath);
            }
            this.districts = parseFeatureCollection(objectMapper.readTree(is));
            log.info("Loaded {} district geometries from {}", districts.size(), resourcePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse district GeoJSON from " + resourcePath, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid district GeoJSON: " + e.getMessage(), e);
        }
    }

    private List<District> parseFeatureCollection(JsonNode root) {
        JsonNode features = root.required("features");
        List<District> loaded = new ArrayList<>();
        for (JsonNode feature : features) {
            String name = feature.path("properties").path("name").asText(null);
            if (name == null || name.isBlank()) {
                log.warn("Skipping district feature without a name");
                continue;
            }
            if (!OFFICIAL_DISTRICTS.contains(name)) {
                log.warn("District '{}' not in official whitelist; mapping to {}", name, UNKNOWN_DISTRICT);
                continue;
            }
            Geometry geometry = parseGeometry(feature.required("geometry"));
            loaded.add(new District(name, geometry));
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("No valid district geometries found in GeoJSON");
        }
        if (loaded.size() != OFFICIAL_DISTRICTS.size()) {
            log.warn("Expected {} official districts but loaded {}; some districts may be missing",
                OFFICIAL_DISTRICTS.size(), loaded.size());
        }
        return List.copyOf(loaded);
    }

    private Geometry parseGeometry(JsonNode geometryNode) {
        String type = geometryNode.required("type").asText();
        JsonNode coordinates = geometryNode.required("coordinates");
        return switch (type) {
            case "Polygon" -> parsePolygon(coordinates);
            case "MultiPolygon" -> parseMultiPolygon(coordinates);
            default -> throw new IllegalArgumentException("Unsupported geometry type: " + type);
        };
    }

    private Polygon parsePolygon(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            throw new IllegalArgumentException("Polygon must have at least one ring");
        }
        LinearRing shell = parseLinearRing(coordinates.get(0));
        LinearRing[] holes = new LinearRing[coordinates.size() - 1];
        for (int i = 1; i < coordinates.size(); i++) {
            holes[i - 1] = parseLinearRing(coordinates.get(i));
        }
        return geometryFactory.createPolygon(shell, holes);
    }

    private MultiPolygon parseMultiPolygon(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            throw new IllegalArgumentException("MultiPolygon must have at least one polygon");
        }
        Polygon[] polygons = new Polygon[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            polygons[i] = parsePolygon(coordinates.get(i));
        }
        return geometryFactory.createMultiPolygon(polygons);
    }

    private LinearRing parseLinearRing(JsonNode ring) {
        if (!ring.isArray() || ring.size() < 4) {
            throw new IllegalArgumentException("Linear ring must contain at least 4 points");
        }
        Coordinate[] coordinates = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            JsonNode point = ring.get(i);
            if (!point.isArray() || point.size() < 2) {
                throw new IllegalArgumentException("Invalid coordinate at index " + i);
            }
            double lon = point.get(0).asDouble();
            double lat = point.get(1).asDouble();
            coordinates[i] = new CoordinateXY(lon, lat);
        }
        return geometryFactory.createLinearRing(coordinates);
    }

    /**
     * Resolves the district for the given coordinate.
     *
     * @param lat latitude in WGS84
     * @param lon longitude in WGS84
     * @return official district name or {@code "Zona no identificada"}
     */
    public String findDistrict(double lat, double lon) {
        return findDistrict(lat, lon, null);
    }

    /**
     * Resolves the district for the given coordinate, using the provided neighborhood name
     * as a fallback hint without calling {@link NeighborhoodLocator} again.
     *
     * @param lat latitude in WGS84
     * @param lon longitude in WGS84
     * @param neighborhoodName optional neighborhood name for step-2 fallback
     * @return official district name or {@code "Zona no identificada"}
     */
    public String findDistrict(double lat, double lon, @Nullable String neighborhoodName) {
        if (isInvalidCoordinate(lat, lon)) {
            return UNKNOWN_DISTRICT;
        }

        // Step 1: when the neighborhood is known, trust the static barrio -> district
        // mapping to keep data coherent and consistent for public reporting.
        if (neighborhoodName != null && !neighborhoodName.isBlank()
            && !UNKNOWN_DISTRICT.equals(neighborhoodName)) {
            String mapped = findDistrictByNeighborhoodName(neighborhoodName);
            if (!UNKNOWN_DISTRICT.equals(mapped)) {
                return mapped;
            }
        }

        // Step 2: point-in-polygon for unknown neighborhoods or unmapped ones.
        Point point = geometryFactory.createPoint(new CoordinateXY(lon, lat));
        for (District district : districts) {
            if (district.geometry.contains(point)) {
                return district.name;
            }
        }

        // Step 3: derive district from the closest neighborhood.
        return resolveByNeighborhood(lat, lon, neighborhoodName);
    }

    private String resolveByNeighborhood(double lat, double lon, @Nullable String neighborhoodName) {
        if (neighborhoodName == null || neighborhoodName.isBlank()
            || UNKNOWN_DISTRICT.equals(neighborhoodName)) {
            neighborhoodName = neighborhoodLocator.findNeighborhood(lat, lon);
        }
        return findDistrictByNeighborhoodName(neighborhoodName);
    }

    /**
     * Resolves the district for a neighborhood name using the static fallback mapping.
     *
     * @param neighborhoodName neighborhood name as resolved by {@link NeighborhoodLocator}
     * @return official district name or {@code "Zona no identificada"}
     */
    public String findDistrictByNeighborhoodName(@Nullable String neighborhoodName) {
        if (neighborhoodName == null || neighborhoodName.isBlank()) {
            return UNKNOWN_DISTRICT;
        }

        String mapped = NEIGHBORHOOD_TO_DISTRICT.get(neighborhoodName);
        if (mapped == null) {
            log.warn("Neighborhood '{}' has no district mapping", neighborhoodName);
            return UNKNOWN_DISTRICT;
        }
        if (!OFFICIAL_DISTRICTS.contains(mapped)) {
            log.warn("Mapped district '{}' for neighborhood '{}' is not official; returning {}",
                mapped, neighborhoodName, UNKNOWN_DISTRICT);
            return UNKNOWN_DISTRICT;
        }
        return mapped;
    }

    private boolean isInvalidCoordinate(double lat, double lon) {
        return (lat == 0.0 && lon == 0.0)
            || Double.isNaN(lat)
            || Double.isNaN(lon)
            || Double.isInfinite(lat)
            || Double.isInfinite(lon);
    }

    private record District(String name, Geometry geometry) {
    }
}
