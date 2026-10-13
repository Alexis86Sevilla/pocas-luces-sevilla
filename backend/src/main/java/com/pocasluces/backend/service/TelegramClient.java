package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pocasluces.backend.config.TelegramProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Minimal Telegram Bot API client: {@code sendMessage} as plain text (no {@code parse_mode},
 * so district and neighborhood names can never be misread as markup), link previews off.
 *
 * <p>The bot token travels in the URL path, which is what the Bot API requires. Nothing
 * this class returns or logs may therefore contain a URL or a raw exception message:
 * every text that could carry the token goes through {@link #sanitize(String)} first.
 * Do not enable DEBUG logging for {@code org.springframework.web.client} in production
 * either, since {@link RestTemplate} logs request URLs at that level.</p>
 */
@Component
public class TelegramClient {

    static final String API_BASE_URL = "https://api.telegram.org";
    static final String REDACTED = "bot***";

    /** Matches the token segment of a Bot API URL, whatever the token is. */
    private static final Pattern BOT_TOKEN_SEGMENT = Pattern.compile("bot[^/\\s\"'<>]+");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramProperties properties;

    public TelegramClient(@Qualifier("telegramRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          TelegramProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Words in a 4xx {@code description} that point at the chat or the bot's rights rather
     * than at the message itself ("chat not found", "CHAT_WRITE_FORBIDDEN", "not enough rights
     * to send text messages to the chat", "bot was kicked from the channel chat", ...).
     * Unknown descriptions default to the destination, i.e. to the conservative outcome.
     */
    private static final List<String> DESTINATION_MARKERS =
        List.of("chat", "rights", "forbidden", "blocked", "kicked", "deactivated", "not found");

    /** Words in a 4xx {@code description} that point at the message body itself. */
    private static final List<String> MESSAGE_MARKERS =
        List.of("message", "text", "entit", "parse", "too long", "empty");

    /** Outcome of one {@code sendMessage} call. Reasons are already sanitized. */
    public sealed interface SendResult {
        record Sent() implements SendResult {}

        /**
         * Any failure other than rate limiting. {@code kind} says whether retrying the same
         * message can help; the caller decides how many times to try before giving up.
         */
        record Failed(String reason, FailureKind kind) implements SendResult {
            /** A failure whose cause is unknown or temporary (retry on the next poll). */
            public Failed(String reason) {
                this(reason, FailureKind.TRANSIENT);
            }
        }

        /** HTTP 429: stop sending for now; {@code retryAfterSeconds} is Telegram's hint (0 if absent). */
        record RateLimited(int retryAfterSeconds) implements SendResult {}
    }

    /** How a {@link SendResult.Failed} should be read by the caller. */
    public enum FailureKind {
        /** Network error, 5xx, unexpected body, 401 (token being rotated): retry on the next poll. */
        TRANSIENT,
        /**
         * Telegram deterministically rejected this message (HTTP 400 about the message body):
         * sending the same text again cannot succeed, other messages are unaffected.
         */
        MESSAGE_REJECTED,
        /**
         * Telegram deterministically refuses to deliver to the configured chat (chat not found,
         * bot kicked or without rights, 403, 404): every message fails the same way until an
         * operator fixes the channel or the configuration.
         */
        CHAT_UNAVAILABLE
    }

    public SendResult sendMessage(String text) {
        if (!properties.enabled()) {
            return new SendResult.Failed("Telegram alerts are disabled");
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                URI.create(API_BASE_URL + "/bot" + properties.botToken() + "/sendMessage"),
                new HttpEntity<>(payload(text), jsonHeaders()),
                String.class);
            return interpret(response);
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            int code = errorCode(body, e.getStatusCode().value());
            if (code == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return new SendResult.RateLimited(retryAfterSeconds(body));
            }
            return new SendResult.Failed(sanitize("HTTP " + e.getStatusCode().value() + " " + description(body)),
                classify(code, description(body)));
        } catch (RuntimeException e) {
            // ResourceAccessException messages include the request URL, hence the token.
            return new SendResult.Failed(sanitize(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /**
     * Permanent failures are the 4xx answers Telegram documents as deterministic: 400 (bad
     * request), 403 (forbidden) and 404 (not found). 403 and 404 are never about one message.
     * A 400 is about the message only when its description clearly says so; a description
     * about the chat or the bot's rights, or an unknown one, counts as the destination, so a
     * misclassification can only make the bot retry for longer, never give up on a message
     * that another poll could have delivered.
     */
    static FailureKind classify(int errorCode, String description) {
        if (errorCode == HttpStatus.FORBIDDEN.value() || errorCode == HttpStatus.NOT_FOUND.value()) {
            return FailureKind.CHAT_UNAVAILABLE;
        }
        if (errorCode != HttpStatus.BAD_REQUEST.value()) {
            return FailureKind.TRANSIENT;
        }
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (DESTINATION_MARKERS.stream().anyMatch(text::contains)) {
            return FailureKind.CHAT_UNAVAILABLE;
        }
        if (MESSAGE_MARKERS.stream().anyMatch(text::contains)) {
            return FailureKind.MESSAGE_REJECTED;
        }
        return FailureKind.CHAT_UNAVAILABLE;
    }

    /** Telegram's own {@code error_code} when the body carries one, else the HTTP status. */
    private int errorCode(String body, int httpStatus) {
        JsonNode root = parse(body);
        return root == null ? httpStatus : root.path("error_code").asInt(httpStatus);
    }

    private String payload(String text) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("chat_id", properties.chatId());
        payload.put("text", text);
        payload.put("disable_web_page_preview", true);
        return payload.toString();
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private SendResult interpret(ResponseEntity<String> response) {
        String body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            return new SendResult.Failed(sanitize("HTTP " + response.getStatusCode().value() + " " + description(body)));
        }
        JsonNode root = parse(body);
        if (root != null && root.path("ok").asBoolean(false)) {
            return new SendResult.Sent();
        }
        return new SendResult.Failed(sanitize("Telegram answered ok=false " + description(body)));
    }

    private int retryAfterSeconds(String body) {
        JsonNode root = parse(body);
        return root == null ? 0 : root.path("parameters").path("retry_after").asInt(0);
    }

    private String description(String body) {
        JsonNode root = parse(body);
        if (root != null && root.hasNonNull("description")) {
            return "(" + root.get("description").asText() + ")";
        }
        return "";
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    String sanitize(String text) {
        return redact(text, properties.botToken());
    }

    /**
     * Removes the bot token from any text: the configured value literally, and any
     * {@code bot<token>} URL segment in case a different token ever appears in a message.
     */
    public static String redact(String text, String botToken) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (botToken != null && !botToken.isBlank()) {
            result = result.replace(botToken, "***");
        }
        return BOT_TOKEN_SEGMENT.matcher(result).replaceAll(REDACTED);
    }
}
