package com.pocasluces.backend.exception;

import com.pocasluces.backend.service.EnelApiService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleGenericShouldNotLeakExceptionMessageToClient() {
        Exception e = new RuntimeException("db password is hunter2, connection to 10.0.0.5 failed");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGeneric(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("hunter2", "10.0.0.5");
    }

    @Test
    void handleEnelApiShouldNotLeakUpstreamErrorTextToClient() {
        EnelApiService.EnelApiException e = new EnelApiService.EnelApiException(
            "Connection refused: internal-enel-host.corp:8443");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleEnelApi(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Upstream Enel API error");
        assertThat(response.getBody().message()).doesNotContain("internal-enel-host.corp");
    }

    @Test
    void handleIllegalArgumentShouldKeepIntentionalValidationMessage() {
        IllegalArgumentException e = new IllegalArgumentException("year must be between 2000 and 2100");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgument(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("year must be between 2000 and 2100");
    }

    @Test
    void handleNotFoundShouldAnswer404WithTheCommonErrorShape() {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "api/nope");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleNotFound(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not found");
        assertThat(response.getBody().message()).isEqualTo("No resource at this path");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleRequestBindingShouldAnswer400ForAMissingParameter() {
        MissingServletRequestParameterException e = new MissingServletRequestParameterException("year", "int");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleRequestBinding(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("year");
    }

    @Test
    void handleMethodNotSupportedShouldAnswer405() {
        HttpRequestMethodNotSupportedException e = new HttpRequestMethodNotSupportedException("GET");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleMethodNotSupported(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
    }

    @Test
    void handleResponseStatusShouldKeepIntentionalReason() {
        ResponseStatusException e = new ResponseStatusException(HttpStatus.BAD_REQUEST, "month is required");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleResponseStatus(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("month is required");
    }
}
