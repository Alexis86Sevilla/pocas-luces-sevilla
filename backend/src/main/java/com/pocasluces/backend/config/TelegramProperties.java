package com.pocasluces.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public Telegram alerts. Both values come from the environment
 * ({@code TELEGRAM_BOT_TOKEN}, {@code TELEGRAM_CHAT_ID}); the feature is active only when
 * both are non-blank. The token is a secret: it is never logged, and every log line that
 * could carry it (URLs, exception messages) is sanitized first. The chat id is public.
 */
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(String botToken, String chatId) {

    public TelegramProperties {
        botToken = botToken == null ? "" : botToken.trim();
        chatId = chatId == null ? "" : chatId.trim();
    }

    public boolean enabled() {
        return !botToken.isBlank() && !chatId.isBlank();
    }
}
