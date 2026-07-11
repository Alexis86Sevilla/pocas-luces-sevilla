package com.pocasluces.backend.controller.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RequestValidation {

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private RequestValidation() {
    }

    public static int requireYear(Integer year) {
        if (year == null) {
            throw badRequest("year is required");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw badRequest("year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
        return year;
    }

    public static void requireMonth(Integer month) {
        if (month == null) {
            throw badRequest("month is required");
        }
        if (month < 1 || month > 12) {
            throw badRequest("month must be between 1 and 12");
        }
    }

    public static int requirePage(int page) {
        if (page < 0) {
            throw badRequest("page must be zero or positive");
        }
        return page;
    }

    public static int requireSize(int size, int maxSize) {
        if (size < 1) {
            throw badRequest("size must be at least 1");
        }
        return Math.min(size, maxSize);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
