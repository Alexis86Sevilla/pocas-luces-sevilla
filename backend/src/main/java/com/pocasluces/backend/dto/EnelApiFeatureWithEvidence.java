package com.pocasluces.backend.dto;

import java.util.Collections;
import java.util.List;

/**
 * An Enel API feature together with the exact evidence that produced it:
 * the page URL and the raw JSON fragment for that feature.
 */
public record EnelApiFeatureWithEvidence(
    EnelApiResponse.Feature feature,
    String sourceUrl,
    String rawResponse
) {

    public static List<EnelApiFeatureWithEvidence> emptyList() {
        return Collections.emptyList();
    }
}
