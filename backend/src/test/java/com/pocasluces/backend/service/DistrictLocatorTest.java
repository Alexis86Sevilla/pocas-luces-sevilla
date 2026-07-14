package com.pocasluces.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistrictLocatorTest {

    private DistrictLocator locator;

    @BeforeEach
    void setUp() {
        locator = new DistrictLocator(new NeighborhoodLocator());
        locator.loadDistricts();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        // Approximate centroids computed from the GeoJSON polygons (WGS84 lat, lon)
        "37.394512, -5.960205, San Pablo-Santa Justa",
        "37.384885, -5.972861, Nervión",
        "37.409122, -5.980490, Macarena",
        "37.356800, -6.009713, Los Remedios",
        "37.346794, -5.972376, Bellavista-La Palmera",
        "37.365798, -5.965949, Sur",
        "37.376516, -5.946746, Cerro-Amate",
        "37.403342, -5.902205, Este-Alcosa-Torreblanca",
        "37.387424, -6.010758, Triana",
        "37.424298, -5.970575, Distrito Norte",
        "37.393897, -5.991379, Casco Antiguo"
    })
    void shouldMatchPointInsideEachDistrict(double lat, double lon, String expectedDistrict) {
        String result = locator.findDistrict(lat, lon);
        assertThat(result).isEqualTo(expectedDistrict);
    }

    @Test
    void shouldMatchKnownLandmarks() {
        // Plaza Nueva — heart of Casco Antiguo
        assertThat(locator.findDistrict(37.3772, -5.9933)).isEqualTo("Casco Antiguo");
        // Puente de Triana / Calle Betis area
        assertThat(locator.findDistrict(37.3860, -6.0050)).isEqualTo("Triana");
    }

    @Test
    void shouldHandleMultiPolygonDistrict() {
        // Los Remedios is the only MultiPolygon in the dataset; use a point in its main polygon
        String result = locator.findDistrict(37.356800, -6.009713);
        assertThat(result).isEqualTo("Los Remedios");
    }

    @Test
    void shouldFallbackToNeighborhoodMappingWhenOutsidePolygons() {
        // East of Torreblanca neighborhood center, outside all district polygons but close
        // enough for NeighborhoodLocator to still resolve "Torreblanca".
        String result = locator.findDistrict(37.3750, -5.9050);
        assertThat(result).isEqualTo("Este-Alcosa-Torreblanca");
    }

    @Test
    void shouldUseProvidedNeighborhoodNameWithoutCallingLocator() {
        // Any coordinate outside polygons; supplying the neighborhood skips the locator.
        String result = locator.findDistrict(37.3750, -5.9050, "Torreblanca");
        assertThat(result).isEqualTo("Este-Alcosa-Torreblanca");
    }

    @Test
    void shouldReturnUnknownForNullIsland() {
        assertThat(locator.findDistrict(0.0, 0.0)).isEqualTo(DistrictLocator.UNKNOWN_DISTRICT);
    }

    @Test
    void shouldReturnUnknownForZeroCoordinatesEvenWithNeighborhoodHint() {
        assertThat(locator.findDistrict(0.0, 0.0, "Triana"))
            .isEqualTo(DistrictLocator.UNKNOWN_DISTRICT);
    }

    @Test
    void shouldReturnUnknownForNaNCoordinates() {
        assertThat(locator.findDistrict(Double.NaN, Double.NaN))
            .isEqualTo(DistrictLocator.UNKNOWN_DISTRICT);
    }

    @Test
    void shouldReturnUnknownForFarAwayCoordinates() {
        // Madrid — no district match and no Sevilla neighborhood within 2 km
        assertThat(locator.findDistrict(40.4168, -3.7038))
            .isEqualTo(DistrictLocator.UNKNOWN_DISTRICT);
    }

    @Test
    void shouldPrioritizeKnownNeighborhoodOverPolygon() {
        // Coordinate inside Nervión district, but neighborhood says Cerro del Águila.
        // With the hybrid approach, the static barrio -> district mapping wins.
        assertThat(locator.findDistrict(37.384885, -5.972861, "Cerro del Águila"))
            .isEqualTo("Cerro-Amate");
    }

    @Test
    void shouldMapAllKnownNeighborhoodsToOfficialDistricts() {
        assertThat(locator.findDistrictByNeighborhoodName("Triana")).isEqualTo("Triana");
        assertThat(locator.findDistrictByNeighborhoodName("La Macarena")).isEqualTo("Macarena");
        assertThat(locator.findDistrictByNeighborhoodName("Nervión")).isEqualTo("Nervión");
        assertThat(locator.findDistrictByNeighborhoodName("San Bernardo")).isEqualTo("Nervión");
        assertThat(locator.findDistrictByNeighborhoodName("Bellavista")).isEqualTo("Bellavista-La Palmera");
        assertThat(locator.findDistrictByNeighborhoodName("San Jerónimo")).isEqualTo("Distrito Norte");
        assertThat(locator.findDistrictByNeighborhoodName("Heliópolis")).isEqualTo("Bellavista-La Palmera");
        assertThat(locator.findDistrictByNeighborhoodName("Los Remedios")).isEqualTo("Los Remedios");
        assertThat(locator.findDistrictByNeighborhoodName("Santa Cruz")).isEqualTo("Casco Antiguo");
        assertThat(locator.findDistrictByNeighborhoodName("San Pablo")).isEqualTo("San Pablo-Santa Justa");
        assertThat(locator.findDistrictByNeighborhoodName("Polígono Sur")).isEqualTo("Sur");
        assertThat(locator.findDistrictByNeighborhoodName("Torreblanca")).isEqualTo("Este-Alcosa-Torreblanca");
        assertThat(locator.findDistrictByNeighborhoodName("Los Pajaritos")).isEqualTo("Cerro-Amate");
        assertThat(locator.findDistrictByNeighborhoodName("Palmete")).isEqualTo("Cerro-Amate");
        assertThat(locator.findDistrictByNeighborhoodName("Cerro del Águila")).isEqualTo("Cerro-Amate");
        assertThat(locator.findDistrictByNeighborhoodName("Pino Montano")).isEqualTo("Distrito Norte");
        assertThat(locator.findDistrictByNeighborhoodName("Alcosa")).isEqualTo("Este-Alcosa-Torreblanca");
        assertThat(locator.findDistrictByNeighborhoodName("Amate")).isEqualTo("Cerro-Amate");
    }

    @Test
    void shouldReturnUnknownForUnmappedNeighborhoodWhenFallbackAlsoFails() {
        // Invented neighborhood name with a far-away coordinate: polygon fails,
        // closest Sevilla neighborhood is beyond 2 km, so nothing resolves.
        assertThat(locator.findDistrict(40.4168, -3.7038, "Barrio Inexistente"))
            .isEqualTo(DistrictLocator.UNKNOWN_DISTRICT);
    }

    @Test
    void shouldFailFastWhenGeoJsonIsMissing() {
        DistrictLocator emptyLocator = new DistrictLocator(new NeighborhoodLocator());
        assertThatThrownBy(() -> emptyLocator.loadDistricts("geojson/does-not-exist.json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("District GeoJSON resource not found");
    }

    @Test
    void shouldFailFastWhenGeoJsonIsInvalid() {
        DistrictLocator emptyLocator = new DistrictLocator(new NeighborhoodLocator());
        assertThatThrownBy(() -> emptyLocator.loadDistricts("geojson/not-a-feature-collection.json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid district GeoJSON");
    }

    @Test
    void lookupShouldBeFast() {
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            locator.findDistrict(37.393897, -5.991379);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 100 lookups must complete in under 100 ms (i.e., 1 ms each with plenty of headroom)
        assertThat(elapsedMs).isLessThan(100);
    }
}
