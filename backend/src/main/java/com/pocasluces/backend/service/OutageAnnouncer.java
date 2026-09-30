package com.pocasluces.backend.service;

import com.pocasluces.backend.config.TelegramProperties;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.service.TelegramClient.SendResult;
import com.pocasluces.backend.service.TelegramMessageFormatter.Message;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Decides, after each successful poll, which outages to announce on Telegram and marks
 * them as announced once Telegram has confirmed the message. Everything is derived from
 * persisted state ({@code announce_eligible}, {@code announced_at},
 * {@code restoration_announced_at}, {@code missing_polls}, plus the ordinary
 * {@code active}/{@code fetched_at}/{@code first_seen_at}), so restarts never cause
 * duplicates and the rules hold whatever the timing of the scheduler.
 *
 * <h2>Two-poll confirmation, both ways</h2>
 * <ul>
 *   <li><b>New:</b> announced only once the outage has been published in the current poll
 *       and in at least one earlier successful poll ({@code fetched_at > first_seen_at}),
 *       so a phantom feature returned by a single partial feed response is never
 *       announced. Outages that started more than {@link #NEW_OUTAGE_MAX_AGE} ago (stale
 *       backlog) or that have not started yet are not announced; rows that predate the
 *       feature ({@code announce_eligible = false}) never are.</li>
 *   <li><b>Restored:</b> announced only for outages this bot announced, once they have
 *       been missing from {@link #MIN_MISSING_POLLS_FOR_RESTORATION} consecutive successful
 *       polls. The scheduler increments {@code missing_polls} in the same transaction as
 *       its resolve step, and the upsert resets it to 0 when the outage reappears, so a
 *       single empty or partial feed response followed by a recovery produces no message.
 *       A failed fetch changes nothing, so it neither counts nor resets.</li>
 * </ul>
 *
 * <h2>Transactions and failure handling</h2>
 * The scheduler calls {@link #announceAfterCommit()} at the end of its transaction; the
 * work runs after the commit (nothing is announced from a rolled-back run), and the
 * candidate query and each "mark announced" update run in their own {@code REQUIRES_NEW}
 * transactions, so Telegram is never called while a database transaction is open. An
 * outage is marked only after Telegram answers {@code ok:true}; on any failure nothing is
 * marked and the same candidates are retried on the next poll. Errors are logged (token
 * redacted) and never propagate to the scheduler.
 */
@Slf4j
@Service
public class OutageAnnouncer {

    static final Duration NEW_OUTAGE_MAX_AGE = Duration.ofHours(12);
    static final int MIN_MISSING_POLLS_FOR_RESTORATION = 2;

    private final EnelOutageRepository repository;
    private final TelegramClient telegramClient;
    private final TelegramMessageFormatter formatter;
    private final TelegramProperties properties;
    private final Clock clock;
    private final TransactionOperations newTransaction;

    @Autowired
    public OutageAnnouncer(EnelOutageRepository repository,
                           TelegramClient telegramClient,
                           TelegramMessageFormatter formatter,
                           TelegramProperties properties,
                           Clock clock,
                           PlatformTransactionManager transactionManager) {
        this(repository, telegramClient, formatter, properties, clock, requiresNew(transactionManager));
    }

    OutageAnnouncer(EnelOutageRepository repository,
                    TelegramClient telegramClient,
                    TelegramMessageFormatter formatter,
                    TelegramProperties properties,
                    Clock clock,
                    TransactionOperations newTransaction) {
        this.repository = repository;
        this.telegramClient = telegramClient;
        this.formatter = formatter;
        this.properties = properties;
        this.clock = clock;
        this.newTransaction = newTransaction;
        if (properties.enabled()) {
            log.info("Telegram alerts enabled for chat {}", properties.chatId());
        } else {
            log.info("Telegram alerts disabled (set TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID to enable)");
        }
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * To be called by the scheduler at the end of a successful run. Inside a transaction
     * the announcement runs after the commit (Spring's afterCommit hook, the same pattern
     * as {@link FetchHealthTracker}); without one it runs immediately. No-op when disabled.
     */
    public void announceAfterCommit() {
        if (!properties.enabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    announcePending();
                }
            });
        } else {
            announcePending();
        }
    }

    /** Selects, sends and marks. Never throws. */
    public void announcePending() {
        if (!properties.enabled()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Candidates candidates = newTransaction.execute(status -> new Candidates(
                repository.findNewOutagesToAnnounce(now, now.minus(NEW_OUTAGE_MAX_AGE)),
                repository.findRestoredOutagesToAnnounce(MIN_MISSING_POLLS_FOR_RESTORATION)));
            if (candidates == null || candidates.isEmpty()) {
                return;
            }
            boolean telegramHealthy = send(formatter.newOutages(candidates.newOutages(), now), "new",
                ids -> repository.markAnnounced(ids, now));
            if (telegramHealthy) {
                send(formatter.restoredOutages(candidates.restoredOutages(), now), "restored",
                    ids -> repository.markRestorationAnnounced(ids, now));
            }
        } catch (RuntimeException e) {
            log.warn("Telegram: announcement step failed, will retry on the next poll: {}",
                TelegramClient.redact(e.getClass().getSimpleName() + ": " + e.getMessage(), properties.botToken()));
        }
    }

    /** @return false when sending should stop for this poll (Telegram failing or rate limiting). */
    private boolean send(List<Message> messages, String kind, Marker marker) {
        for (Message message : messages) {
            SendResult result = telegramClient.sendMessage(message.text());
            switch (result) {
                case SendResult.Sent sent -> {
                    newTransaction.executeWithoutResult(status -> marker.mark(message.outageIds()));
                    log.info("Telegram: announced {} {} outage(s)", message.outageIds().size(), kind);
                }
                case SendResult.RateLimited limited -> {
                    log.warn("Telegram: rate limited (retry after {} s); skipping the rest of this poll",
                        limited.retryAfterSeconds());
                    return false;
                }
                case SendResult.Failed failed -> {
                    log.warn("Telegram: could not send {} outages message, will retry on the next poll: {}",
                        kind, failed.reason());
                    return false;
                }
            }
        }
        return true;
    }

    private record Candidates(List<EnelOutage> newOutages, List<EnelOutage> restoredOutages) {
        boolean isEmpty() {
            return newOutages.isEmpty() && restoredOutages.isEmpty();
        }
    }

    private interface Marker {
        void mark(Collection<Long> outageIds);
    }
}
