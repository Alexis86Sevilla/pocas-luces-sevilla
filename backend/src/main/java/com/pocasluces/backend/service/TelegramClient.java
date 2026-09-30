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

    /** Outcome of one {@code sendMessage} call. Reasons are already sanitized. */
    public sealed interface SendResult {
        record Sent() implements SendResult {}

        /** Any failure other than rate limiting; the caller may retry on the next poll. */
        record Failed(String reason) implements SendResult {}

        /** HTTP 429: stop sending for now; {@code retryAfterSeconds} is Telegram's hint (0 if absent). */
        record RateLimited(int retryAfterSeconds) implements SendResult {}
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
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return new SendResult.RateLimited(retryAfterSeconds(body));
            }
            return new SendResult.Failed(sanitize("HTTP " + e.getStatusCode().value() + " " + description(body)));
        } catch (RuntimeException e) {
            // ResourceAccessException messages include the request URL, hence the token.
            return new SendResult.Failed(sanitize(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
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
