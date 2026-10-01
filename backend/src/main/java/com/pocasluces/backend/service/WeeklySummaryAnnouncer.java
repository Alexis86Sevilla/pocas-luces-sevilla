package com.pocasluces.backend.service;

import com.pocasluces.backend.config.TelegramProperties;
import com.pocasluces.backend.repository.WeeklySummaryRepository;
import com.pocasluces.backend.service.TelegramClient.SendResult;
import com.pocasluces.backend.service.WeeklySummaryFormatter.Summary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.OptionalLong;

/**
 * Posts one summary of the week that just ended to the public Telegram channel.
 *
 * <h2>When</h2>
 * Checked after every successful poll (right after the outage alerts, see
 * {@link OutageDataScheduler}). It acts only on Monday (Europe/Madrid) from
 * {@link #SEND_FROM} to the end of the day, and only for the week that ended the previous
 * Sunday. If the application was down all Monday that week is skipped rather than posted late.
 *
 * <h2>At most once</h2>
 * A week is recorded in {@code telegram_weekly_summary} only after Telegram answers
 * {@code ok:true} (insert-if-absent). On any other result nothing is recorded and the next
 * poll the same day retries. If Telegram confirmed but recording the week failed, the week is
 * also kept in memory so the same JVM never posts it twice; only a restart in that narrow
 * window could duplicate it.
 *
 * <h2>Honesty rules</h2>
 * <ul>
 *   <li>The week-over-week comparison is included only when our data fully covers both
 *       weeks, defined as: the earliest {@code first_seen_at} over all rows is at or before
 *       the start of the previous week. Otherwise the line is left out (no fabricated trend).</li>
 *   <li>A zero-outage week is reported as such only when we were already collecting data
 *       during that week (earliest {@code first_seen_at} before the end of the week);
 *       otherwise nothing is posted, since we could not know.</li>
 *   <li>The top list ignores the unidentified-zone placeholder but totals include it.</li>
 * </ul>
 *
 * Never throws: any failure is logged (token redacted) and cannot affect alerts or the poll.
 */
@Slf4j
@Service
public class WeeklySummaryAnnouncer {

    static final LocalTime SEND_FROM = LocalTime.of(9, 0);

    private final WeeklySummaryRepository repository;
    private final TelegramClient telegramClient;
    private final WeeklySummaryFormatter formatter;
    private final TelegramProperties properties;
    private final Clock clock;
    private final TransactionOperations newTransaction;
    /** Week confirmed sent by this JVM whose mark could not be persisted (see class javadoc). */
    private volatile LocalDate sentButUnmarked;

    @Autowired
    public WeeklySummaryAnnouncer(WeeklySummaryRepository repository,
                                  TelegramClient telegramClient,
                                  WeeklySummaryFormatter formatter,
                                  TelegramProperties properties,
                                  Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this(repository, telegramClient, formatter, properties, clock, requiresNew(transactionManager));
    }

    WeeklySummaryAnnouncer(WeeklySummaryRepository repository,
                           TelegramClient telegramClient,
                           WeeklySummaryFormatter formatter,
                           TelegramProperties properties,
                           Clock clock,
                           TransactionOperations newTransaction) {
        this.repository = repository;
        this.telegramClient = telegramClient;
        this.formatter = formatter;
        this.properties = properties;
        this.clock = clock;
        this.newTransaction = newTransaction;
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /** Same pattern as {@link OutageAnnouncer#announceAfterCommit()}; no-op when disabled. */
    public void announceAfterCommit() {
        if (!properties.enabled()) {
            return;
        }
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        announceIfDue();
                    }
                });
            } else {
                announceIfDue();
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    /** Sends the summary when due. Never throws. */
    public void announceIfDue() {
        if (!properties.enabled()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            if (now.getDayOfWeek() != DayOfWeek.MONDAY || now.toLocalTime().isBefore(SEND_FROM)) {
                return;
            }
            LocalDate weekStart = now.toLocalDate().minusDays(7);
            if (weekStart.equals(sentButUnmarked)
                || Boolean.TRUE.equals(newTransaction.execute(s -> repository.isWeekSent(weekStart)))) {
                return;
            }
            Summary summary = newTransaction.execute(s -> buildSummary(weekStart));
            if (summary == null) {
                return;
            }
            SendResult result = telegramClient.sendMessage(formatter.format(summary));
            switch (result) {
                case SendResult.Sent sent -> {
                    log.info("Telegram: weekly summary for the week of {} sent", weekStart);
                    markSent(weekStart, now);
                }
                case SendResult.RateLimited limited -> log.warn(
                    "Telegram: rate limited sending the weekly summary (retry after {} s); will retry on the next poll",
                    limited.retryAfterSeconds());
                case SendResult.Failed failed -> log.warn(
                    "Telegram: could not send the weekly summary, will retry on the next poll: {}", failed.reason());
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    /** @return the summary to post, or null when the week must not be summarized (logged). */
    private Summary buildSummary(LocalDate weekStart) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekStart.plusDays(7).atStartOfDay();
        LocalDateTime earliest = repository.earliestFirstSeen();
        var totals = repository.totals(from, to);
        if (totals.outages() == 0 && (earliest == null || !earliest.isBefore(to))) {
            log.info("Telegram: no data collected during the week of {}; weekly summary skipped", weekStart);
            return null;
        }
        LocalDateTime previousFrom = weekStart.minusDays(7).atStartOfDay();
        OptionalLong previous = earliest != null && !earliest.isAfter(previousFrom)
            ? OptionalLong.of(repository.totals(previousFrom, from).outages())
            : OptionalLong.empty();
        var districts = totals.outages() == 0 ? List.<WeeklySummaryRepository.DistrictCount>of()
            : repository.districtCounts(from, to);
        return new Summary(weekStart, totals.outages(), totals.affectedClients(), totals.briefOutages(),
            districts, previous);
    }

    private void markSent(LocalDate weekStart, LocalDateTime now) {
        try {
            newTransaction.executeWithoutResult(s -> repository.markWeekSent(weekStart, now));
        } catch (RuntimeException e) {
            sentButUnmarked = weekStart;
            log.warn("Telegram: weekly summary was sent but recording it failed; held in memory to avoid reposting: {}",
                redacted(e));
        }
    }

    private void logFailure(RuntimeException e) {
        log.warn("Telegram: weekly summary step failed, will retry on the next poll: {}", redacted(e));
    }

    private String redacted(RuntimeException e) {
        return TelegramClient.redact(e.getClass().getSimpleName() + ": " + e.getMessage(), properties.botToken());
    }
}
