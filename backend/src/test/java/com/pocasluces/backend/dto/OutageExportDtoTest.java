package com.pocasluces.backend.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutageExportDtoTest {

    @Test
    void toCsvRowShouldPrefixValuesStartingWithFormulaCharactersAndStillQuoteWhenNeeded() {
        OutageExportDto dto = dto("=cmd|'/c calc,'!A1", "San Pablo", "GB");

        String row = dto.toCsvRow();

        // The value contains a comma, so it is also wrapped in double quotes per the
        // existing CSV quoting rule, on top of the leading single-quote neutralization.
        assertThat(row).contains("\"'=cmd|'/c calc,'!A1\"");
    }

    @Test
    void toCsvRowShouldPrefixValuesStartingWithPlusMinusAtTabOrCarriageReturn() {
        assertThat(dto("+SUM(A1:A2)", "n", "s").toCsvRow()).contains(",'+SUM(A1:A2),");
        assertThat(dto("-2+3", "n", "s").toCsvRow()).contains(",'-2+3,");
        assertThat(dto("@SUM(A1:A2)", "n", "s").toCsvRow()).contains(",'@SUM(A1:A2),");
        assertThat(dto("\tmalicious", "n", "s").toCsvRow()).contains(",'\tmalicious,");
        // A leading CR also makes the existing quoting rule kick in (it already treats
        // any embedded \r as needing double-quote wrapping).
        assertThat(dto("\rmalicious", "n", "s").toCsvRow()).contains(",\"'\rmalicious\",");
    }

    @Test
    void toCsvRowShouldNotAlterOrdinaryStringValues() {
        String row = dto("obj-1", "San Pablo", "GB").toCsvRow();

        assertThat(row).contains("obj-1", "San Pablo", "GB");
        assertThat(row).doesNotContain("'obj-1");
    }

    @Test
    void toCsvRowShouldNeverPrefixNumericColumns() {
        OutageExportDto dto = new OutageExportDto(
            -5L, "obj-1", "San Pablo", "San Pablo-Santa Justa", "GB",
            "2026-07-10T08:30:00", "2026-07-10T09:00:00",
            -3, -5.960205, -37.394512,
            "http://source", "hash", "2026-07-10T08:00:00", "2026-07-10T08:00:00"
        );

        String row = dto.toCsvRow();

        assertThat(row).startsWith("-5,");
        assertThat(row).contains(",-3,-5.960205,-37.394512,");
    }

    @Test
    void headerShouldListAllColumns() {
        assertThat(OutageExportDto.header())
            .isEqualTo("id,objectId,neighborhoodName,districtName,serviceType,interruptionDate,repositionDate," +
                "affectedClients,latitude,longitude,sourceUrl,rawResponseHash,firstSeenAt,fetchedAt");
    }

    private OutageExportDto dto(String objectId, String neighborhoodName, String serviceType) {
        return new OutageExportDto(
            1L, objectId, neighborhoodName, "San Pablo-Santa Justa", serviceType,
            "2026-07-10T08:30:00", "2026-07-10T09:00:00",
            10, 37.394512, -5.960205,
            "http://source", "hash", "2026-07-10T08:00:00", "2026-07-10T08:00:00"
        );
    }
}
