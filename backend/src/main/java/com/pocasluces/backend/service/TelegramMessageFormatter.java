package com.pocasluces.backend.service;

import com.pocasluces.backend.entity.EnelOutage;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the plain-text Telegram messages (Spanish, impersonal). One grouped message per
 * poll and type, split into several messages only when the Telegram length limit requires
 * it, and capped at {@link #MAX_MESSAGES_PER_TYPE}: beyond that the rest is summarized as
 * "y N más" with a link to the map, and those outages count as announced too.
 *
 * <p>Everything stated comes from persisted data: the start time and the restoration
 * estimate are Endesa's own values, the neighborhood is marked as approximate, and the
 * observed duration is a lower bound ({@code resolvedAt} is the last poll in which the
 * outage was still published). Nothing is inferred beyond what the website itself shows.</p>
 */
@Component
public class TelegramMessageFormatter {

    /** Telegram's hard limit is 4096 characters; keep a margin (emoji count double in Java). */
    static final int MAX_MESSAGE_LENGTH = 4000;
    static final int MAX_MESSAGES_PER_TYPE = 3;

    static final String SITE_URL = "https://sevillasinluz.es";
    static final String MAP_URL = SITE_URL + "/mapa";
    static final String FOOTER = "Datos de e-distribución · " + SITE_URL;
    static final String NEW_HEADER_SINGULAR = "🔴 Nuevo corte de luz en Sevilla";
    static final String NEW_HEADER_PLURAL = "🔴 Nuevos cortes de luz en Sevilla";
    static final String RESTORED_HEADER = "🟢 Luz restablecida";
    static final String CATEGORY_FAULT = "Avería";
    static final String CATEGORY_SCHEDULED = "Trabajos programados";

    /** The scheduler's placeholder when coordinates could not be located. */
    static final String UNKNOWN_ZONE = "Zona no identificada";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");

    /** One Telegram message and the outages it announces (including any summarized ones). */
    public record Message(String text, List<Long> outageIds) {}

    private record Line(String text, Long outageId) {}

    public List<Message> newOutages(List<EnelOutage> outages, LocalDateTime now) {
        List<Line> lines = outages.stream().map(o -> new Line(newOutageLine(o, now), o.getId())).toList();
        return pack(lines, count -> count == 1 ? NEW_HEADER_SINGULAR : NEW_HEADER_PLURAL);
    }

    public List<Message> restoredOutages(List<EnelOutage> outages, LocalDateTime now) {
        List<Line> lines = outages.stream().map(o -> new Line(restoredLine(o, now), o.getId())).toList();
        return pack(lines, count -> RESTORED_HEADER);
    }

    String newOutageLine(EnelOutage o, LocalDateTime now) {
        StringBuilder line = new StringBuilder("• ").append(place(o));
        line.append(" desde ").append(sinceWhen(o.getInterruptionDate(), now));
        if (o.getAffectedClients() != null) {
            line.append(" · ").append(supplies(o.getAffectedClients()));
        }
        line.append(" · ").append(category(o.getCause(), o.getServiceType()));
        if (o.getRepositionDate() != null) {
            line.append(" · reposición estimada ").append(dayAndTime(o.getRepositionDate(), now));
        }
        return line.toString();
    }

    String restoredLine(EnelOutage o, LocalDateTime now) {
        StringBuilder line = new StringBuilder("• ").append(place(o));
        line.append(", corte desde ").append(sinceWhen(o.getInterruptionDate(), now));
        String duration = observedDuration(o.getInterruptionDate(), o.getResolvedAt());
        if (duration != null) {
            line.append(" · duración observada: al menos ").append(duration);
        }
        return line.toString();
    }

    /** Same rule as the website and the open-data CSV: Endesa's cause first, LV as fallback. */
    static String category(String cause, String serviceType) {
        if (CATEGORY_SCHEDULED.equals(cause)) {
            return CATEGORY_SCHEDULED;
        }
        if (CATEGORY_FAULT.equals(cause)) {
            return CATEGORY_FAULT;
        }
        return "LV".equals(serviceType) ? CATEGORY_SCHEDULED : CATEGORY_FAULT;
    }

    /** "2 h 35 min", "45 min", "3 h"; null when there is no positive whole minute to report. */
    static String observedDuration(LocalDateTime start, LocalDateTime resolvedAt) {
        if (start == null || resolvedAt == null) {
            return null;
        }
        long minutes = Duration.between(start, resolvedAt).toMinutes();
        if (minutes <= 0) {
            return null;
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    private static String place(EnelOutage o) {
        String district = blankToUnknown(o.getDistrictName());
        String neighborhood = o.getNeighborhoodName();
        if (neighborhood == null || neighborhood.isBlank() || UNKNOWN_ZONE.equals(neighborhood)
            || neighborhood.equals(district)) {
            return district;
        }
        return district + " (" + neighborhood + " aprox.)";
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN_ZONE : value;
    }

    /** "las 10:59" today, "el 29/09 a las 23:50" otherwise. */
    private static String sinceWhen(LocalDateTime when, LocalDateTime now) {
        if (when.toLocalDate().equals(now.toLocalDate())) {
            return "las " + when.format(TIME);
        }
        return "el " + when.format(DAY) + " a las " + when.format(TIME);
    }

    /** "13:30" today, "30/09 13:30" otherwise. */
    private static String dayAndTime(LocalDateTime when, LocalDateTime now) {
        if (when.toLocalDate().equals(now.toLocalDate())) {
            return when.format(TIME);
        }
        return when.format(DAY) + " " + when.format(TIME);
    }

    private static String supplies(int affectedClients) {
        String number = NumberFormat.getIntegerInstance(SPANISH).format(affectedClients);
        return number + (affectedClients == 1 ? " suministro" : " suministros");
    }

    private interface Header {
        String forCount(int lineCount);
    }

    private static List<Message> pack(List<Line> lines, Header header) {
        if (lines.isEmpty()) {
            return List.of();
        }
        List<List<Line>> chunks = splitIntoChunks(lines, header);
        if (chunks.size() <= MAX_MESSAGES_PER_TYPE) {
            return chunks.stream().map(chunk -> render(chunk, header, List.of())).toList();
        }

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < MAX_MESSAGES_PER_TYPE - 1; i++) {
            messages.add(render(chunks.get(i), header, List.of()));
        }
        List<Line> last = new ArrayList<>(chunks.get(MAX_MESSAGES_PER_TYPE - 1));
        List<Line> summarized = new ArrayList<>();
        for (int i = MAX_MESSAGES_PER_TYPE; i < chunks.size(); i++) {
            summarized.addAll(chunks.get(i));
        }
        // Make room for the summary line by moving trailing lines into the summary.
        while (last.size() > 1 && render(last, header, summarized).text().length() > MAX_MESSAGE_LENGTH) {
            summarized.add(0, last.remove(last.size() - 1));
        }
        messages.add(render(last, header, summarized));
        return messages;
    }

    private static List<List<Line>> splitIntoChunks(List<Line> lines, Header header) {
        List<List<Line>> chunks = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        for (Line line : lines) {
            current.add(line);
            if (current.size() > 1 && render(current, header, List.of()).text().length() > MAX_MESSAGE_LENGTH) {
                current.remove(current.size() - 1);
                chunks.add(current);
                current = new ArrayList<>(List.of(line));
            }
        }
        chunks.add(current);
        return chunks;
    }

    private static Message render(List<Line> lines, Header header, List<Line> summarized) {
        StringBuilder text = new StringBuilder(header.forCount(lines.size() + summarized.size()));
        List<Long> ids = new ArrayList<>();
        for (Line line : lines) {
            text.append('\n').append(line.text());
            ids.add(line.outageId());
        }
        if (!summarized.isEmpty()) {
            text.append("\n… y ").append(summarized.size()).append(" más en ").append(MAP_URL);
            summarized.forEach(line -> ids.add(line.outageId()));
        }
        text.append('\n').append(FOOTER);
        return new Message(text.toString(), List.copyOf(ids));
    }
}
