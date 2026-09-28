package com.pocasluces.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void shouldRejectMissingKeyWhenConfigured() {
        ApiKeyAuth apiKeyAuth = new ApiKeyAuth("expected-key");
        when(request.getHeader("X-API-Key")).thenReturn(null);

        assertThatThrownBy(() -> apiKeyAuth.requireValidKey(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWrongKey() {
        ApiKeyAuth apiKeyAuth = new ApiKeyAuth("expected-key");
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");

        assertThatThrownBy(() -> apiKeyAuth.requireValidKey(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptCorrectKey() {
        ApiKeyAuth apiKeyAuth = new ApiKeyAuth("expected-key");
        when(request.getHeader("X-API-Key")).thenReturn("expected-key");

        assertThatCode(() -> apiKeyAuth.requireValidKey(request)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWithForbiddenWhenKeyNotConfigured() {
        ApiKeyAuth apiKeyAuth = new ApiKeyAuth("");

        assertThatThrownBy(() -> apiKeyAuth.requireValidKey(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }
}
