package com.pocasluces.backend.service;

import com.pocasluces.backend.repository.WeeklySummaryRepository.DistrictCount;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Builds the plain-text weekly summary (Spanish, impersonal, same footer and style as
 * {@link TelegramMessageFormatter}). Only persisted aggregates are stated; the comparison
 * line appears only when the caller passes the previous week's total, which it does only
 * when our data fully covers both weeks.
 */
@Component
public class WeeklySummaryFormatter {

    static final String HEADER = "📊 Resumen semanal de cortes de luz en Sevilla";
    static final int TOP_DISTRICTS = 3;

    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH);
    private static final DateTimeFormatter DAY_ONLY = DateTimeFormatter.ofPattern("d", SPANISH);

    /** Everything the summary states about one week. */
    public record Summary(LocalDate weekStart, long outages, long affectedClients, long briefOutages,
                          List<DistrictCount> districts, OptionalLong previousWeekOutages) {}

    public String format(Summary summary) {
        StringBuilder text = new StringBuilder(HEADER).append('\n').append(weekRange(summary.weekStart()));
        if (summary.outages() == 0) {
            text.append("\nNo se publicó ningún corte de luz en Sevilla durante esa semana.");
            return text.append('\n').append(TelegramMessageFormatter.FOOTER).toString();
        }
        text.append("\n• ").append(totalsLine(summary));
        String top = topLine(summary.districts());
        if (top != null) {
            text.append("\n• ").append(top);
        }
        if (summary.previousWeekOutages().isPresent()) {
            text.append("\n• ").append(comparisonLine(summary.outages(), summary.previousWeekOutages().getAsLong()));
        }
        return text.append('\n').append(TelegramMessageFormatter.FOOTER).toString();
    }

    /** "Semana del 21 al 27 de septiembre", "Semana del 29 de septiembre al 5 de octubre". */
    static String weekRange(LocalDate weekStart) {
        LocalDate end = weekStart.plusDays(6);
        if (weekStart.getYear() != end.getYear()) {
            return "Semana del " + weekStart.format(DAY_MONTH_YEAR) + " al " + end.format(DAY_MONTH_YEAR);
        }
        if (weekStart.getMonth() != end.getMonth()) {
            return "Semana del " + weekStart.format(DAY_MONTH) + " al " + end.format(DAY_MONTH);
        }
        return "Semana del " + weekStart.format(DAY_ONLY) + " al " + end.format(DAY_MONTH);
    }

    private static String totalsLine(Summary s) {
        String outages = number(s.outages()) + (s.outages() == 1 ? " corte" : " cortes");
        String brief = s.briefOutages() == 0 ? ""
            : " (" + number(s.briefOutages()) + (s.briefOutages() == 1 ? " breve)" : " breves)");
        String supplies = number(s.affectedClients())
            + (s.affectedClients() == 1 ? " suministro afectado" : " suministros afectados");
        return outages + brief + " · " + supplies;
    }

    /** Top districts by outages, ties alphabetical (Spanish collation); the unidentified zone is skipped. */
    private static String topLine(List<DistrictCount> districts) {
        Collator collator = Collator.getInstance(SPANISH);
        List<DistrictCount> top = districts.stream()
            .filter(d -> d.district() != null && !d.district().isBlank()
                && !TelegramMessageFormatter.UNKNOWN_ZONE.equals(d.district()))
            .sorted(Comparator.comparingLong(DistrictCount::outages).reversed()
                .thenComparing(DistrictCount::district, collator))
            .limit(TOP_DISTRICTS)
            .toList();
        if (top.isEmpty()) {
            return null;
        }
        String items = String.join(", ", top.stream().map(d -> d.district() + " (" + d.outages() + ")").toList());
        return (top.size() == 1 ? "Zona más afectada: " : "Más afectados: ") + items;
    }

    private static String comparisonLine(long current, long previous) {
        long diff = current - previous;
        if (diff == 0) {
            return "Mismos cortes que la semana anterior";
        }
        long abs = Math.abs(diff);
        String count = number(abs) + (abs == 1 ? " corte" : " cortes");
        return count + (diff > 0 ? " más" : " menos") + " que la semana anterior";
    }

    private static String number(long value) {
        return NumberFormat.getIntegerInstance(SPANISH).format(value);
    }
}
