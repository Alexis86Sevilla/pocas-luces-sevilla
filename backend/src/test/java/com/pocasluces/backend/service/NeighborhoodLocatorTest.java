package com.pocasluces.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NeighborhoodLocatorTest {

    private final NeighborhoodLocator locator = new NeighborhoodLocator();

    @Test
    void shouldIdentifySanPablo() {
        String result = locator.findNeighborhood(37.3970, -5.9800);
        assertThat(result).isEqualTo("San Pablo");
    }

    @Test
    void shouldIdentifyPoligonoSur() {
        String result = locator.findNeighborhood(37.3630, -5.9750);
        assertThat(result).isEqualTo("Polígono Sur");
    }

    @Test
    void shouldIdentifyTriana() {
        String result = locator.findNeighborhood(37.3830, -6.0050);
        assertThat(result).isEqualTo("Triana");
    }

    @Test
    void shouldIdentifyLosRemedios() {
        String result = locator.findNeighborhood(37.3750, -6.0050);
        assertThat(result).isEqualTo("Los Remedios");
    }

    @Test
    void shouldReturnNullForFarCoordinates() {
        String result = locator.findNeighborhood(37.2800, -5.9200);
        assertThat(result).isNull();
    }

    @Test
    void shouldMapEnelOutageToNeighborhood() {
        String result = locator.findNeighborhood(37.3847, -5.9887);
        assertThat(result).isNotNull();
        assertThat(result).isIn("San Pablo", "Nervión", "Triana", "San Bernardo", "Santa Cruz");
    }

    @ParameterizedTest
    @CsvSource({
        "37.3970, -5.9800, San Pablo",
        "37.3630, -5.9750, Polígono Sur",
        "37.3750, -6.0050, Los Remedios",
        "37.3850, -5.9900, Santa Cruz"
    })
    void shouldMatchKnownCoordinates(double lat, double lon, String expected) {
        assertThat(locator.findNeighborhood(lat, lon)).isEqualTo(expected);
    }

    @Test
    void shouldReturnNullForCoordinatesWithNullIsland() {
        assertThat(locator.findNeighborhood(0.0, 0.0)).isNull();
    }

    @Test
    void shouldReturnNullForNorthernHemisphereOutsideSeville() {
        assertThat(locator.findNeighborhood(40.4168, -3.7038)).isNull();
    }
}
