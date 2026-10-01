package com.pocasluces.backend.service;

import com.pocasluces.backend.repository.WeeklySummaryRepository.DistrictCount;
import com.pocasluces.backend.service.WeeklySummaryFormatter.Summary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklySummaryFormatterTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);
    private final WeeklySummaryFormatter formatter = new WeeklySummaryFormatter();

    private static Summary summary(LocalDate week, long outages, long clients, long brief,
                                   List<DistrictCount> districts, OptionalLong previous) {
        return new Summary(week, outages, clients, brief, districts, previous);
    }

    @Test
    void rendersANormalWeek() {
        String text = formatter.format(summary(WEEK, 85, 12_345, 12, List.of(
            new DistrictCount("Cerro-Amate", 14), new DistrictCount("Este-Alcosa-Torreblanca", 31),
            new DistrictCount("Triana", 9), new DistrictCount("Nervión", 2)), OptionalLong.of(73)));

        assertThat(text).isEqualTo("""
            📊 Resumen semanal de cortes de luz en Sevilla
            Semana del 21 al 27 de septiembre
            • 85 cortes (12 breves) · 12.345 suministros afectados
            • Más afectados: Este-Alcosa-Torreblanca (31), Cerro-Amate (14), Triana (9)
            • 12 cortes más que la semana anterior
            Datos de e-distribución · https://sevillasinluz.es""");
    }

    @Test
    void rendersAZeroOutageWeekAsAShortMessage() {
        String text = formatter.format(summary(WEEK, 0, 0, 0, List.of(), OptionalLong.of(5)));

        assertThat(text).isEqualTo("""
            📊 Resumen semanal de cortes de luz en Sevilla
            Semana del 21 al 27 de septiembre
            No se publicó ningún corte de luz en Sevilla durante esa semana.
            Datos de e-distribución · https://sevillasinluz.es""");
    }

    @Test
    void writesTheRangeAcrossMonthsAndYears() {
        assertThat(WeeklySummaryFormatter.weekRange(LocalDate.of(2026, 9, 29)))
            .isEqualTo("Semana del 29 de septiembre al 5 de octubre");
        assertThat(WeeklySummaryFormatter.weekRange(LocalDate.of(2025, 12, 29)))
            .isEqualTo("Semana del 29 de diciembre de 2025 al 4 de enero de 2026");
    }

    @Test
    void usesSingularForms() {
        String text = formatter.format(summary(WEEK, 1, 1, 1,
            List.of(new DistrictCount("Triana", 1)), OptionalLong.of(2)));

        assertThat(text).contains("• 1 corte (1 breve) · 1 suministro afectado");
        assertThat(text).contains("• Zona más afectada: Triana (1)");
        assertThat(text).contains("• 1 corte menos que la semana anterior");
    }

    @Test
    void saysNoneWereBriefWhenThereAreNone() {
        String text = formatter.format(summary(WEEK, 3, 30, 0, List.of(), OptionalLong.empty()));

        assertThat(text).contains("• 3 cortes · 30 suministros afectados");
    }

    @Test
    void ordersTiesAlphabeticallyAndKeepsOnlyTheTopThree() {
        String text = formatter.format(summary(WEEK, 20, 200, 0, List.of(
            new DistrictCount("Triana", 5), new DistrictCount("Bellavista-La Palmera", 5),
            new DistrictCount("Nervión", 5), new DistrictCount("Casco Antiguo", 5)), OptionalLong.empty()));

        assertThat(text).contains("• Más afectados: Bellavista-La Palmera (5), Casco Antiguo (5), Nervión (5)\n");
        assertThat(text).doesNotContain("Triana");
    }

    @Test
    void leavesTheUnidentifiedZoneOutOfTheTopList() {
        String text = formatter.format(summary(WEEK, 10, 100, 0, List.of(
            new DistrictCount("Zona no identificada", 6), new DistrictCount("Triana", 4)), OptionalLong.empty()));

        assertThat(text).contains("• Zona más afectada: Triana (4)");
        assertThat(text).doesNotContain("Zona no identificada");
    }

    @Test
    void omitsTheTopLineWhenNoDistrictIsIdentified() {
        String text = formatter.format(summary(WEEK, 2, 20, 0,
            List.of(new DistrictCount("Zona no identificada", 2)), OptionalLong.empty()));

        assertThat(text).doesNotContain("afectados:").doesNotContain("afectada:");
    }

    @Test
    void omitsTheComparisonWhenThePreviousWeekIsNotCovered() {
        String text = formatter.format(summary(WEEK, 5, 50, 0, List.of(), OptionalLong.empty()));

        assertThat(text).doesNotContain("semana anterior");
    }

    @Test
    void wordsAnEqualWeekWithoutAMoreOrLess() {
        String text = formatter.format(summary(WEEK, 5, 50, 0, List.of(), OptionalLong.of(5)));

        assertThat(text).contains("• Mismos cortes que la semana anterior");
    }
}
