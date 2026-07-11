package com.pocasluces.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnelApiResponse {
    private List<Feature> features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {
        private Attributes attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        @JsonProperty("objectid1")
        private String objectId;

        private String municipality;

        @JsonProperty("cd_code")
        private String cdCode;

        private Double latitude;
        private Double longitude;

        @JsonProperty("affected_client")
        private Integer affectedClient;

        @JsonProperty("interruption_date")
        private String interruptionDate;

        @JsonProperty("reposition_date")
        private String repositionDate;

        @JsonProperty("service_type")
        private String serviceType;

        @JsonProperty("des_cause_es")
        private String cause;

        @JsonProperty("service_des_es")
        private String serviceDescription;

        private String note;

        @JsonProperty("cod_cause")
        private String causeCode;
    }
}
