package com.pocasluces.backend.service;

import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.service.TelegramMessageFormatter.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 11, 0);
    private static final String FOOTER = "Datos de e-distribución · https://sevillasinluz.es";

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    @Test
    void newOutageLineListsEveryKnownField() {
        EnelOutage o = outage(1, "Triana", "León", NOW.minusMinutes(1));
        o.setRepositionDate(LocalDateTime.of(2026, 9, 29, 13, 30));

        assertThat(formatter.newOutageLine(o, NOW))
            .isEqualTo("• Triana (León aprox.) desde las 10:59 · 120 suministros · Avería · reposición estimada 13:30");
    }

    @Test
    void newOutageLineOmitsWhatIsUnknown() {
        EnelOutage o = outage(1, "Triana", "Zona no identificada", NOW.minusMinutes(1));
        o.setAffectedClients(null);
        o.setRepositionDate(null);

        assertThat(formatter.newOutageLine(o, NOW)).isEqualTo("• Triana desde las 10:59 · Avería");
    }

    @Test
    void newOutageLineFallsBackToUnknownZoneAndSkipsARedundantNeighborhood() {
        EnelOutage unknown = outage(1, null, null, NOW.minusMinutes(1));
        EnelOutage same = outage(2, "Nervión", "Nervión", NOW.minusMinutes(1));

        assertThat(formatter.newOutageLine(unknown, NOW)).startsWith("• Zona no identificada desde las 10:59");
        assertThat(formatter.newOutageLine(same, NOW)).startsWith("• Nervión desde las 10:59");
    }

    @Test
    void newOutageLineSpellsOutTheDayWhenNotToday() {
        EnelOutage o = outage(1, "Triana", "León", LocalDateTime.of(2026, 9, 28, 23, 50));
        o.setRepositionDate(LocalDateTime.of(2026, 9, 30, 2, 0));

        assertThat(formatter.newOutageLine(o, NOW))
            .isEqualTo("• Triana (León aprox.) desde el 28/09 a las 23:50 · 120 suministros · Avería · reposición estimada 30/09 02:00");
    }

    @Test
    void categoryFollowsTheSiteRule() {
        assertThat(TelegramMessageFormatter.category("Trabajos programados", "AT")).isEqualTo("Trabajos programados");
        assertThat(TelegramMessageFormatter.category("Avería", "LV")).isEqualTo("Avería");
        assertThat(TelegramMessageFormatter.category(null, "LV")).isEqualTo("Trabajos programados");
        assertThat(TelegramMessageFormatter.category(null, "AT")).isEqualTo("Avería");
        assertThat(TelegramMessageFormatter.category("Otra cosa", "BT")).isEqualTo("Avería");
    }

    @Test
    void suppliesUseSpanishThousandsSeparatorAndSingular() {
        EnelOutage many = outage(1, "Triana", "León", NOW.minusMinutes(1));
        many.setAffectedClients(1200);
        EnelOutage one = outage(2, "Triana", "León", NOW.minusMinutes(1));
        one.setAffectedClients(1);

        assertThat(formatter.newOutageLine(many, NOW)).contains("· 1.200 suministros ·");
        assertThat(formatter.newOutageLine(one, NOW)).contains("· 1 suministro ·");
    }

    @Test
    void restoredLineReportsALowerBoundDuration() {
        EnelOutage o = outage(1, "Triana", "León", LocalDateTime.of(2026, 9, 29, 8, 0));
        o.setActive(false);
        o.setResolvedAt(LocalDateTime.of(2026, 9, 29, 10, 35));

        assertThat(formatter.restoredLine(o, NOW))
            .isEqualTo("• Triana (León aprox.), corte desde las 08:00 · duración observada: al menos 2 h 35 min");
    }

    @Test
    void restoredLineOmitsTheDurationWhenItIsNotPositive() {
        EnelOutage o = outage(1, "Triana", "León", LocalDateTime.of(2026, 9, 29, 8, 0));
        o.setResolvedAt(LocalDateTime.of(2026, 9, 29, 7, 59));

        assertThat(formatter.restoredLine(o, NOW)).isEqualTo("• Triana (León aprox.), corte desde las 08:00");
        o.setResolvedAt(null);
        assertThat(formatter.restoredLine(o, NOW)).isEqualTo("• Triana (León aprox.), corte desde las 08:00");
    }

    @Test
    void observedDurationFormats() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 8, 0);
        assertThat(TelegramMessageFormatter.observedDuration(start, start.plusMinutes(45))).isEqualTo("45 min");
        assertThat(TelegramMessageFormatter.observedDuration(start, start.plusHours(3))).isEqualTo("3 h");
        assertThat(TelegramMessageFormatter.observedDuration(start, start.plusMinutes(155))).isEqualTo("2 h 35 min");
        assertThat(TelegramMessageFormatter.observedDuration(start, start.plusSeconds(30))).isNull();
        assertThat(TelegramMessageFormatter.observedDuration(start, start)).isNull();
    }

    @Test
    void groupsNewOutagesUnderAPluralHeaderWithFooter() {
        List<Message> messages = formatter.newOutages(List.of(
            outage(1, "Triana", "León", NOW.minusMinutes(1)),
            outage(2, "Nervión", "Nervión", NOW.minusMinutes(2))), NOW);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).text()).isEqualTo("""
            🔴 Nuevos cortes de luz en Sevilla
            • Triana (León aprox.) desde las 10:59 · 120 suministros · Avería
            • Nervión desde las 10:58 · 120 suministros · Avería
            """ + FOOTER);
        assertThat(messages.get(0).outageIds()).containsExactly(1L, 2L);
    }

    @Test
    void usesSingularHeaderForASingleNewOutage() {
        List<Message> messages = formatter.newOutages(List.of(outage(1, "Triana", "León", NOW.minusMinutes(1))), NOW);

        assertThat(messages.get(0).text()).startsWith("🔴 Nuevo corte de luz en Sevilla\n");
    }

    @Test
    void groupsRestoredOutagesUnderTheirHeader() {
        EnelOutage o = outage(1, "Triana", "León", NOW.minusHours(2));
        o.setResolvedAt(NOW.minusMinutes(10));

        List<Message> messages = formatter.restoredOutages(List.of(o), NOW);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).text()).isEqualTo("""
            🟢 Luz restablecida
            • Triana (León aprox.), corte desde las 09:00 · duración observada: al menos 1 h 50 min
            """ + FOOTER);
    }

    @Test
    void returnsNoMessageForNoOutages() {
        assertThat(formatter.newOutages(List.of(), NOW)).isEmpty();
        assertThat(formatter.restoredOutages(List.of(), NOW)).isEmpty();
    }

    @Test
    void splitsIntoSeveralMessagesUnderTheLengthLimitWithoutLosingOrDuplicatingAnyOutage() {
        // ~150 characters per line: 40 lines need two messages, well under the cap of three.
        List<EnelOutage> outages = manyOutages(40);

        List<Message> messages = formatter.newOutages(outages, NOW);

        assertThat(messages).hasSizeGreaterThan(1).hasSizeLessThanOrEqualTo(TelegramMessageFormatter.MAX_MESSAGES_PER_TYPE);
        for (Message m : messages) {
            assertThat(m.text().length()).isLessThanOrEqualTo(TelegramMessageFormatter.MAX_MESSAGE_LENGTH);
            assertThat(m.text()).startsWith("🔴 Nuevos cortes de luz en Sevilla\n").endsWith("\n" + FOOTER).doesNotContain("más en");
        }
        assertThat(allIds(messages)).containsExactlyElementsOf(outages.stream().map(EnelOutage::getId).toList());
    }

    @Test
    void capsTheNumberOfMessagesAndSummarizesTheRestAsAnnounced() {
        List<EnelOutage> outages = manyOutages(400);

        List<Message> messages = formatter.newOutages(outages, NOW);

        assertThat(messages).hasSize(TelegramMessageFormatter.MAX_MESSAGES_PER_TYPE);
        for (Message m : messages) {
            assertThat(m.text().length()).isLessThanOrEqualTo(TelegramMessageFormatter.MAX_MESSAGE_LENGTH);
        }
        String last = messages.get(messages.size() - 1).text();
        assertThat(last).containsPattern("\n… y \\d+ más en https://sevillasinluz\\.es/mapa\n" + FOOTER + "$");
        assertThat(allIds(messages))
            .as("summarized outages count as announced, and nothing is announced twice")
            .containsExactlyInAnyOrderElementsOf(outages.stream().map(EnelOutage::getId).toList());
        Set<Long> unique = new HashSet<>(allIds(messages));
        assertThat(unique).hasSize(outages.size());
    }

    private static List<Long> allIds(List<Message> messages) {
        List<Long> ids = new ArrayList<>();
        messages.forEach(m -> ids.addAll(m.outageIds()));
        return ids;
    }

    private static List<EnelOutage> manyOutages(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> {
                EnelOutage o = outage(i, "Distrito de nombre largo número " + i, "Barrio con nombre también largo " + i,
                    NOW.minusMinutes(i % 60));
                o.setRepositionDate(NOW.plusHours(1));
                return o;
            })
            .toList();
    }

    private static EnelOutage outage(long id, String district, String neighborhood, LocalDateTime start) {
        return EnelOutage.builder()
            .id(id).objectId(String.valueOf(id)).latitude(37.38).longitude(-5.99).serviceType("AT")
            .interruptionDate(start).districtName(district).neighborhoodName(neighborhood)
            .affectedClients(120).cause("Avería")
            .firstSeenAt(start).fetchedAt(NOW).createdAt(start).updatedAt(NOW)
            .build();
    }
}
