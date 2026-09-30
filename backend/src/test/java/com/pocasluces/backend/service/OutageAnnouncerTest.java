package com.pocasluces.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.config.TelegramProperties;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.service.TelegramClient.SendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

@ExtendWith(MockitoExtension.class)
class OutageAnnouncerTest {

    /** Deliberately not shaped like a real token (no "bot<digits>:" pattern) so the repo grep stays clean. */
    private static final String TOKEN = "fake-token";
    private static final TelegramProperties ENABLED = new TelegramProperties(TOKEN, "@SevillaSinLuz");
    private static final TelegramProperties DISABLED = new TelegramProperties("", "@SevillaSinLuz");

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 11, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(MADRID).toInstant(), MADRID);

    @Mock
    private EnelOutageRepository repository;

    @Mock
    private TelegramClient client;

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(OutageAnnouncer.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(OutageAnnouncer.class)).detachAppender(logs);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OutageAnnouncer announcer(TelegramProperties properties) {
        return new OutageAnnouncer(repository, client, formatter, properties, clock, TransactionOperations.withoutTransaction());
    }

    @Test
    void doesNothingAtAllWhenDisabled() {
        OutageAnnouncer announcer = announcer(DISABLED);

        announcer.announceAfterCommit();
        announcer.announcePending();

        verifyNoInteractions(repository, client);
        assertThat(messages()).anyMatch(m -> m.contains("Telegram alerts disabled"));
    }

    @Test
    void logsEnabledChatAtStartupWithoutTheToken() {
        announcer(ENABLED);

        assertThat(messages()).anyMatch(m -> m.equals("Telegram alerts enabled for chat @SevillaSinLuz"));
        assertThat(String.join("\n", messages())).doesNotContain(TOKEN);
    }

    @Test
    void sendsNewThenRestoredAndMarksEachOnlyAfterTelegramConfirms() {
        EnelOutage fresh = outage(1L, "Triana", "León", NOW.minusMinutes(20));
        EnelOutage restored = outage(2L, "Nervión", "Nervión", NOW.minusHours(3));
        restored.setActive(false);
        restored.setResolvedAt(NOW.minusMinutes(15));
        when(repository.findNewOutagesToAnnounce(NOW, NOW.minus(OutageAnnouncer.NEW_OUTAGE_MAX_AGE))).thenReturn(List.of(fresh));
        when(repository.findRestoredOutagesToAnnounce(OutageAnnouncer.MIN_MISSING_POLLS_FOR_RESTORATION)).thenReturn(List.of(restored));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());

        announcer(ENABLED).announcePending();

        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).sendMessage(texts.capture());
        assertThat(texts.getAllValues().get(0)).startsWith("🔴 Nuevo corte de luz en Sevilla\n• Triana (León aprox.) desde las 10:40");
        assertThat(texts.getAllValues().get(1)).startsWith("🟢 Luz restablecida\n• Nervión, corte desde las 08:00 · duración observada: al menos 2 h 45 min");
        verify(repository).markAnnounced(List.of(1L), NOW);
        verify(repository).markRestorationAnnounced(List.of(2L), NOW);
    }

    @Test
    void skipsTelegramEntirelyWhenThereIsNothingToAnnounce() {
        when(repository.findNewOutagesToAnnounce(any(), any())).thenReturn(List.of());
        when(repository.findRestoredOutagesToAnnounce(anyInt())).thenReturn(List.of());

        announcer(ENABLED).announcePending();

        verifyNoInteractions(client);
    }

    @Test
    void doesNotMarkAnythingAndStopsForThisPollWhenTelegramFails() {
        when(repository.findNewOutagesToAnnounce(any(), any())).thenReturn(List.of(outage(1L, "Triana", "León", NOW.minusMinutes(20))));
        when(repository.findRestoredOutagesToAnnounce(anyInt())).thenReturn(List.of(inactive(2L)));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Failed("HTTP 502 (Bad Gateway)"));

        announcer(ENABLED).announcePending();

        verify(client, times(1)).sendMessage(anyString());
        verify(repository, never()).markAnnounced(any(), any());
        verify(repository, never()).markRestorationAnnounced(any(), any());
        assertThat(warnings()).singleElement().asString().contains("HTTP 502").contains("retry on the next poll");
    }

    @Test
    void skipsTheRestOfThePollWhenRateLimited() {
        when(repository.findNewOutagesToAnnounce(any(), any())).thenReturn(List.of(outage(1L, "Triana", "León", NOW.minusMinutes(20))));
        when(repository.findRestoredOutagesToAnnounce(anyInt())).thenReturn(List.of(inactive(2L)));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.RateLimited(7));

        announcer(ENABLED).announcePending();

        verify(client, times(1)).sendMessage(anyString());
        verify(repository, never()).markAnnounced(any(), any());
        assertThat(warnings()).singleElement().asString().contains("rate limited").contains("7 s");
    }

    @Test
    void marksTheMessagesThatWereSentBeforeAFailure() {
        EnelOutage first = outage(1L, "Triana", "León", NOW.minusMinutes(20));
        when(repository.findNewOutagesToAnnounce(any(), any())).thenReturn(List.of(first));
        when(repository.findRestoredOutagesToAnnounce(anyInt())).thenReturn(List.of(inactive(2L)));
        when(client.sendMessage(anyString()))
            .thenReturn(new SendResult.Sent())
            .thenReturn(new SendResult.Failed("HTTP 500"));

        announcer(ENABLED).announcePending();

        verify(repository).markAnnounced(List.of(1L), NOW);
        verify(repository, never()).markRestorationAnnounced(any(), any());
    }

    @Test
    void neverPropagatesExceptionsAndRedactsTheTokenFromThem() {
        when(repository.findNewOutagesToAnnounce(any(), any()))
            .thenThrow(new IllegalStateException("db down while calling https://api.telegram.org/bot" + TOKEN + "/sendMessage"));

        announcer(ENABLED).announcePending();

        assertThat(warnings()).singleElement().asString()
            .contains("IllegalStateException").contains("bot***").doesNotContain(TOKEN);
    }

    @Test
    void transportFailureFromTheRealClientIsLoggedWithoutTheToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(anything()).andRespond(withException(new IOException("connection reset")));
        TelegramClient realClient = new TelegramClient(restTemplate, new ObjectMapper(), ENABLED);
        when(repository.findNewOutagesToAnnounce(any(), any())).thenReturn(List.of(outage(1L, "Triana", "León", NOW.minusMinutes(20))));
        when(repository.findRestoredOutagesToAnnounce(anyInt())).thenReturn(List.of());
        OutageAnnouncer announcer = new OutageAnnouncer(repository, realClient, formatter, ENABLED, clock,
            TransactionOperations.withoutTransaction());

        announcer.announcePending();

        verify(repository, never()).markAnnounced(any(), any());
        String allLogs = String.join("\n", messages());
        assertThat(allLogs).contains("connection reset").contains("bot***").doesNotContain(TOKEN);
    }

    @Test
    void insideATransactionTheWorkRunsOnlyAfterCommit() {
        when(repository.findNewOutagesToAnnounce(any(), any())).thenReturn(List.of(outage(1L, "Triana", "León", NOW.minusMinutes(20))));
        when(repository.findRestoredOutagesToAnnounce(anyInt())).thenReturn(List.of());
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());
        TransactionSynchronizationManager.initSynchronization();

        announcer(ENABLED).announceAfterCommit();
        verifyNoInteractions(client);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(client).sendMessage(anyString());
        verify(repository).markAnnounced(List.of(1L), NOW);
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static EnelOutage inactive(long id) {
        EnelOutage o = outage(id, "Nervión", "Nervión", NOW.minusHours(3));
        o.setActive(false);
        o.setResolvedAt(NOW.minusMinutes(15));
        return o;
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
