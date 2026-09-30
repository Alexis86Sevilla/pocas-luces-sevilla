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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
 *
 * <h2>Mark failing after a confirmed send</h2>
 * If Telegram confirmed a message but the "mark announced" update fails (for example a
 * transient database error), the mark is retried immediately up to {@link #MARK_ATTEMPTS}
 * times. If it still fails, the outage ids and the mark to apply are remembered in memory
 * as "sent but not yet marked": the rest of that poll is skipped, every later run first
 * retries those marks, and those outages are excluded from the candidates of their kind
 * while pending, so the same public message is never posted twice because of a database
 * hiccup. <b>Remaining limitation:</b> that pending state lives only in memory, so a JVM
 * restart between a confirmed send and a successful mark can still produce exactly one
 * duplicate message for that outage.
 */
@Slf4j
@Service
public class OutageAnnouncer {

    static final Duration NEW_OUTAGE_MAX_AGE = Duration.ofHours(12);
    static final int MIN_MISSING_POLLS_FOR_RESTORATION = 2;
    static final int MARK_ATTEMPTS = 3;
    private static final String KIND_NEW = "new";
    private static final String KIND_RESTORED = "restored";

    private final EnelOutageRepository repository;
    private final TelegramClient telegramClient;
    private final TelegramMessageFormatter formatter;
    private final TelegramProperties properties;
    private final Clock clock;
    private final TransactionOperations newTransaction;
    /** Messages Telegram confirmed whose mark could not be persisted yet; see the class javadoc. */
    private final List<PendingMark> pendingMarks = new CopyOnWriteArrayList<>();

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
            retryPendingMarks();
            Candidates found = newTransaction.execute(status -> new Candidates(
                repository.findNewOutagesToAnnounce(now, now.minus(NEW_OUTAGE_MAX_AGE)),
                repository.findRestoredOutagesToAnnounce(MIN_MISSING_POLLS_FOR_RESTORATION)));
            if (found == null) {
                return;
            }
            Set<Long> pendingNew = pendingIds(KIND_NEW);
            Set<Long> pendingRestored = pendingIds(KIND_RESTORED);
            Candidates candidates = new Candidates(
                found.newOutages().stream().filter(o -> !pendingNew.contains(o.getId())).toList(),
                found.restoredOutages().stream().filter(o -> !pendingRestored.contains(o.getId())).toList());
            if (candidates.isEmpty()) {
                return;
            }
            boolean telegramHealthy = send(formatter.newOutages(candidates.newOutages(), now), KIND_NEW,
                ids -> repository.markAnnounced(ids, now));
            if (telegramHealthy) {
                send(formatter.restoredOutages(candidates.restoredOutages(), now), KIND_RESTORED,
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
                    log.info("Telegram: announced {} {} outage(s)", message.outageIds().size(), kind);
                    if (!markWithRetries(message.outageIds(), marker)) {
                        pendingMarks.add(new PendingMark(kind, List.copyOf(message.outageIds()), marker));
                        log.warn("Telegram: a {} message was sent but marking {} outage(s) as announced failed after {} attempts; "
                                + "they are held in memory, excluded from further sends and their mark is retried on the next poll",
                            kind, message.outageIds().size(), MARK_ATTEMPTS);
                        return false;
                    }
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

    /** Marks in a new transaction, retrying immediately; @return whether one attempt succeeded. */
    private boolean markWithRetries(Collection<Long> ids, Marker marker) {
        for (int attempt = 1; attempt <= MARK_ATTEMPTS; attempt++) {
            try {
                newTransaction.executeWithoutResult(status -> marker.mark(ids));
                return true;
            } catch (RuntimeException e) {
                log.warn("Telegram: marking announced outages failed (attempt {}/{}): {}", attempt, MARK_ATTEMPTS,
                    TelegramClient.redact(e.getClass().getSimpleName() + ": " + e.getMessage(), properties.botToken()));
            }
        }
        return false;
    }

    /** Retries the marks left over from earlier polls; successful ones leave the pending list. */
    private void retryPendingMarks() {
        for (PendingMark pending : pendingMarks) {
            if (markWithRetries(pending.ids(), pending.marker())) {
                pendingMarks.remove(pending);
                log.info("Telegram: marked {} previously sent {} outage(s) as announced", pending.ids().size(), pending.kind());
            }
        }
    }

    private Set<Long> pendingIds(String kind) {
        Set<Long> ids = new HashSet<>();
        for (PendingMark pending : pendingMarks) {
            if (pending.kind().equals(kind)) {
                ids.addAll(pending.ids());
            }
        }
        return ids;
    }

    private record PendingMark(String kind, List<Long> ids, Marker marker) {
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
