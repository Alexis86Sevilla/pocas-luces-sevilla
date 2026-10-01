package com.pocasluces.backend.service;

import com.pocasluces.backend.config.TelegramProperties;
import com.pocasluces.backend.repository.WeeklySummaryRepository;
import com.pocasluces.backend.repository.WeeklySummaryRepository.DistrictCount;
import com.pocasluces.backend.repository.WeeklySummaryRepository.Totals;
import com.pocasluces.backend.service.TelegramClient.SendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklySummaryAnnouncerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final TelegramProperties ENABLED = new TelegramProperties("fake-token", "@SevillaSinLuz");
    private static final TelegramProperties DISABLED = new TelegramProperties("", "@SevillaSinLuz");
    /** Monday 2026-09-28; the summarized week starts Monday 2026-09-21. */
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);
    private static final LocalDateTime WEEK_START = WEEK.atStartOfDay();
    private static final LocalDateTime WEEK_END = WEEK.plusDays(7).atStartOfDay();
    private static final LocalDateTime PREVIOUS_START = WEEK.minusDays(7).atStartOfDay();

    @Mock
    private WeeklySummaryRepository repository;

    @Mock
    private TelegramClient client;

    private final WeeklySummaryFormatter formatter = new WeeklySummaryFormatter();

    private WeeklySummaryAnnouncer announcer(TelegramProperties properties, LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(MADRID).toInstant(), MADRID);
        return new WeeklySummaryAnnouncer(repository, client, formatter, properties, clock,
            TransactionOperations.withoutTransaction());
    }

    private void dataFor(LocalDateTime earliestFirstSeen) {
        when(repository.isWeekSent(WEEK)).thenReturn(false);
        when(repository.earliestFirstSeen()).thenReturn(earliestFirstSeen);
        when(repository.totals(WEEK_START, WEEK_END)).thenReturn(new Totals(4, 40, 1));
        when(repository.districtCounts(WEEK_START, WEEK_END)).thenReturn(List.of(new DistrictCount("Triana", 3)));
    }

    @Test
    void sendsAndMarksTheWeekOnMondayAtNine() {
        dataFor(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repository.totals(PREVIOUS_START, WEEK_START)).thenReturn(new Totals(2, 0, 0));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 9, 0);

        announcer(ENABLED, now).announceIfDue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(text.capture());
        assertThat(text.getValue()).contains("Semana del 21 al 27 de septiembre")
            .contains("• 4 cortes (1 breve) · 40 suministros afectados")
            .contains("• 2 cortes más que la semana anterior");
        verify(repository).markWeekSent(WEEK, now);
    }

    @Test
    void skipsTheComparisonWhenTheDataDoesNotCoverThePreviousWeek() {
        dataFor(LocalDateTime.of(2026, 9, 22, 10, 0));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0)).announceIfDue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(text.capture());
        assertThat(text.getValue()).doesNotContain("semana anterior");
        verify(repository, never()).totals(PREVIOUS_START, WEEK_START);
    }

    @Test
    void coverageStartingExactlyAtThePreviousWeekStartCounts() {
        dataFor(PREVIOUS_START);
        when(repository.totals(PREVIOUS_START, WEEK_START)).thenReturn(new Totals(4, 0, 0));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0)).announceIfDue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(text.capture());
        assertThat(text.getValue()).contains("Mismos cortes que la semana anterior");
    }

    @Test
    void sendsUntilTheLastSecondOfMonday() {
        dataFor(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repository.totals(PREVIOUS_START, WEEK_START)).thenReturn(new Totals(4, 0, 0));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 23, 59, 59)).announceIfDue();

        verify(client).sendMessage(anyString());
    }

    @Test
    void doesNothingOutsideTheMondayWindow() {
        for (LocalDateTime now : List.of(
            LocalDateTime.of(2026, 9, 27, 12, 0),   // Sunday
            LocalDateTime.of(2026, 9, 28, 8, 59),   // Monday before 09:00
            LocalDateTime.of(2026, 9, 29, 0, 0),    // Tuesday
            LocalDateTime.of(2026, 9, 29, 12, 0))) {
            announcer(ENABLED, now).announceIfDue();
        }

        verifyNoInteractions(client, repository);
    }

    @Test
    void doesNotSendAWeekThatWasAlreadySent() {
        when(repository.isWeekSent(WEEK)).thenReturn(true);

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0)).announceIfDue();

        verifyNoInteractions(client);
        verify(repository, never()).markWeekSent(any(), any());
    }

    @Test
    void doesNotMarkAndRetriesOnTheNextPollWhenTelegramFails() {
        dataFor(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repository.totals(PREVIOUS_START, WEEK_START)).thenReturn(new Totals(4, 0, 0));
        when(client.sendMessage(anyString()))
            .thenReturn(new SendResult.Failed("HTTP 502"))
            .thenReturn(new SendResult.RateLimited(3))
            .thenReturn(new SendResult.Sent());
        WeeklySummaryAnnouncer announcer = announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0));

        announcer.announceIfDue();
        announcer.announceIfDue();
        verify(repository, never()).markWeekSent(any(), any());

        announcer.announceIfDue();
        verify(client, times(3)).sendMessage(anyString());
        verify(repository).markWeekSent(WEEK, LocalDateTime.of(2026, 9, 28, 10, 0));
    }

    @Test
    void neverRepostsInTheSameJvmWhenMarkingFailsAfterASend() {
        dataFor(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repository.totals(PREVIOUS_START, WEEK_START)).thenReturn(new Totals(4, 0, 0));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());
        when(repository.markWeekSent(any(), any())).thenThrow(new IllegalStateException("db down"));
        WeeklySummaryAnnouncer announcer = announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0));

        announcer.announceIfDue();
        announcer.announceIfDue();

        verify(client, times(1)).sendMessage(anyString());
    }

    @Test
    void neverThrowsWhenTheDatabaseFails() {
        when(repository.isWeekSent(WEEK)).thenThrow(new IllegalStateException("db down"));

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0)).announceIfDue();

        verifyNoInteractions(client);
    }

    @Test
    void postsAZeroWeekWhenWeWereAlreadyCollectingData() {
        when(repository.isWeekSent(WEEK)).thenReturn(false);
        when(repository.earliestFirstSeen()).thenReturn(LocalDateTime.of(2026, 9, 1, 0, 0));
        when(repository.totals(WEEK_START, WEEK_END)).thenReturn(new Totals(0, 0, 0));
        when(repository.totals(PREVIOUS_START, WEEK_START)).thenReturn(new Totals(3, 0, 0));
        when(client.sendMessage(anyString())).thenReturn(new SendResult.Sent());

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0)).announceIfDue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(text.capture());
        assertThat(text.getValue()).contains("No se publicó ningún corte de luz");
    }

    @Test
    void skipsAZeroWeekThatPredatesOurDataCollection() {
        when(repository.isWeekSent(WEEK)).thenReturn(false);
        when(repository.earliestFirstSeen()).thenReturn(null);
        when(repository.totals(WEEK_START, WEEK_END)).thenReturn(new Totals(0, 0, 0));

        announcer(ENABLED, LocalDateTime.of(2026, 9, 28, 10, 0)).announceIfDue();

        verifyNoInteractions(client);
        verify(repository, never()).markWeekSent(any(), any());
    }

    @Test
    void doesNothingAtAllWhenDisabled() {
        WeeklySummaryAnnouncer announcer = announcer(DISABLED, LocalDateTime.of(2026, 9, 28, 10, 0));

        announcer.announceAfterCommit();
        announcer.announceIfDue();

        verifyNoInteractions(client, repository);
    }
}
