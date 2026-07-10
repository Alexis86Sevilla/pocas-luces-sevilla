package com.pocasluces.backend.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NeighborhoodLocatorTest {

    private final NeighborhoodLocator locator = new NeighborhoodLocator();

    @Test
    void shouldIdentifySanPablo() {
        // Coordenadas cerca del centro de San Pablo
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
        // Coordenadas en Dos Hermanas (~12km de Sevilla)
        String result = locator.findNeighborhood(37.2800, -5.9200);
        assertThat(result).isNull();
    }

    @Test
    void shouldMapEnelOutageToNeighborhood() {
        // Corte real: lat 37.3847, lon -5.9887
        // Esto está entre San Pablo y Nervión, más cerca de Nervión
        String result = locator.findNeighborhood(37.3847, -5.9887);
        assertThat(result).isNotNull();
        // Debe ser uno de los barrios cercanos
        assertThat(result).isIn("San Pablo", "Nervión", "Triana", "San Bernardo", "Santa Cruz");
    }
}
