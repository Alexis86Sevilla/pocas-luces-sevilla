package com.pocasluces.backend.exception;

import com.pocasluces.backend.service.EnelApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(LocalDateTime.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                "Bad request", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        log.warn("{}: {}", status, e.getReason());
        return ResponseEntity.status(status)
            .body(new ErrorResponse(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), e.getReason()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "Invalid value for parameter '" + e.getName() + "'";
        log.warn("{}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(),
                "Bad request", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(LocalDateTime.now(), HttpStatus.CONFLICT.value(),
                "Conflict", "A record with the same key already exists"));
    }

    @ExceptionHandler(EnelApiService.EnelApiException.class)
    public ResponseEntity<ErrorResponse> handleEnelApi(EnelApiService.EnelApiException e) {
        log.warn("Enel API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_GATEWAY.value(),
                "Bad gateway", "Upstream Enel API error: " + e.getMessage()));
    }

    public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message) {
    }
}
