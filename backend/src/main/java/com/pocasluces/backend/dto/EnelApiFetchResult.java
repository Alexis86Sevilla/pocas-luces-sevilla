package com.pocasluces.backend.dto;

import java.util.Collections;
import java.util.List;

public record EnelApiFetchResult(String sourceUrl, String rawResponse, List<EnelApiResponse.Feature> features) {

    public EnelApiFetchResult {
        features = features != null ? List.copyOf(features) : Collections.emptyList();
    }
}
