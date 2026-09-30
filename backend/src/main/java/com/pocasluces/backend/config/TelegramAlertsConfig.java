package com.pocasluces.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Wiring for the public Telegram alerts. The Telegram client gets its own
 * {@link RestTemplate} with short timeouts: it runs on the scheduler thread right after
 * each poll commits, so a slow Telegram API must never hold up the next poll for long.
 */
@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramAlertsConfig {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    @Bean
    public RestTemplate telegramRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
