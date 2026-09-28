package com.pocasluces.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyAuth {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyAuth(@Value("${admin.api.key:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    public void requireValidKey(HttpServletRequest request) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin API key is not configured");
        }
        String provided = request.getHeader(API_KEY_HEADER);
        if (provided == null || !constantTimeEquals(expectedApiKey, provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
    }
}
