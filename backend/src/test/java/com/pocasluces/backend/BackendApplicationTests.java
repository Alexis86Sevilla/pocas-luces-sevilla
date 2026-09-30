package com.pocasluces.backend;

import com.pocasluces.backend.config.TelegramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class BackendApplicationTests {

	@Autowired
	private TelegramProperties telegramProperties;

	@Test
	void contextLoads() {
	}

	@Test
	void telegramAlertsAreDisabledWithoutEnvironmentVariables() {
		// No TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID in the test environment: the feature is
		// off, and OutageAnnouncer therefore never touches Telegram.
		assertThat(telegramProperties.enabled()).isFalse();
		assertThat(telegramProperties.botToken()).isEmpty();
	}

}
