package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.config.TelegramProperties;
import com.pocasluces.backend.service.TelegramClient.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramClientTest {

    /** Deliberately not shaped like a real token (no "bot<digits>:" pattern). */
    private static final String TOKEN = "fake-token";
    private static final String SEND_URL = TelegramClient.API_BASE_URL + "/bot" + TOKEN + "/sendMessage";
    private static final TelegramProperties PROPS = new TelegramProperties(TOKEN, "@SevillaSinLuz");

    private MockRestServiceServer server;
    private TelegramClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new TelegramClient(restTemplate, new ObjectMapper(), PROPS);
    }

    @Test
    void postsPlainTextJsonWithoutParseModeAndWithoutLinkPreviews() {
        server.expect(requestTo(SEND_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.chat_id").value("@SevillaSinLuz"))
            .andExpect(jsonPath("$.text").value("🔴 Nuevo corte de luz en Sevilla\n• Triana"))
            .andExpect(jsonPath("$.disable_web_page_preview").value(true))
            .andExpect(jsonPath("$.parse_mode").doesNotExist())
            .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));

        SendResult result = client.sendMessage("🔴 Nuevo corte de luz en Sevilla\n• Triana");

        assertThat(result).isInstanceOf(SendResult.Sent.class);
        server.verify();
    }

    @Test
    void treatsOkFalseAsFailure() {
        server.expect(requestTo(SEND_URL))
            .andRespond(withSuccess("{\"ok\":false,\"description\":\"Bad Request: chat not found\"}", MediaType.APPLICATION_JSON));

        SendResult result = client.sendMessage("x");

        assertThat(result).isInstanceOfSatisfying(SendResult.Failed.class,
            f -> assertThat(f.reason()).contains("ok=false").contains("chat not found"));
    }

    @Test
    void treatsServerErrorsAsFailure() {
        server.expect(requestTo(SEND_URL)).andRespond(withServerError());

        SendResult result = client.sendMessage("x");

        assertThat(result).isInstanceOfSatisfying(SendResult.Failed.class,
            f -> assertThat(f.reason()).startsWith("HTTP 500"));
    }

    @Test
    void treatsClientErrorsAsFailureWithTelegramsDescription() {
        server.expect(requestTo(SEND_URL))
            .andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot is not a member of the channel chat\"}"));

        SendResult result = client.sendMessage("x");

        assertThat(result).isInstanceOfSatisfying(SendResult.Failed.class,
            f -> assertThat(f.reason()).isEqualTo("HTTP 403 (Forbidden: bot is not a member of the channel chat)"));
    }

    @Test
    void reportsRateLimitingWithTelegramsRetryAfter() {
        server.expect(requestTo(SEND_URL))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests: retry after 7\",\"parameters\":{\"retry_after\":7}}"));

        SendResult result = client.sendMessage("x");

        assertThat(result).isEqualTo(new SendResult.RateLimited(7));
    }

    @Test
    void redactsTheTokenFromTransportErrorsWhoseMessageContainsTheUrl() {
        server.expect(requestTo(SEND_URL)).andRespond(withException(new IOException("Connection refused")));

        SendResult result = client.sendMessage("x");

        assertThat(result).isInstanceOfSatisfying(SendResult.Failed.class, f -> assertThat(f.reason())
            .contains("ResourceAccessException")
            .contains("Connection refused")
            .contains("bot***")
            .doesNotContain(TOKEN));
    }

    @Test
    void makesNoHttpCallWhenDisabled() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer strictServer = MockRestServiceServer.createServer(restTemplate);
        TelegramClient disabled = new TelegramClient(restTemplate, new ObjectMapper(), new TelegramProperties("", "@SevillaSinLuz"));

        SendResult result = disabled.sendMessage("x");

        assertThat(result).isInstanceOf(SendResult.Failed.class);
        strictServer.verify();
    }

    @Test
    void redactRemovesTheConfiguredTokenAndAnyBotUrlSegment() {
        assertThat(TelegramClient.redact("POST https://api.telegram.org/bot" + TOKEN + "/sendMessage failed", TOKEN))
            .isEqualTo("POST https://api.telegram.org/bot***/sendMessage failed");
        assertThat(TelegramClient.redact("https://api.telegram.org/botOTHER_SECRET/getMe", TOKEN))
            .isEqualTo("https://api.telegram.org/bot***/getMe");
        assertThat(TelegramClient.redact("Forbidden: bot is not a member", TOKEN))
            .isEqualTo("Forbidden: bot is not a member");
        assertThat(TelegramClient.redact(null, TOKEN)).isEmpty();
    }
}
