package com.pocasluces.backend.controller;

import com.pocasluces.backend.controller.validation.RequestValidation;
import com.pocasluces.backend.dto.OpenDataOutageRow;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Public, unauthenticated open-data download (CC BY 4.0). Deliberately outside
 * {@code /api/outages/export}, which nginx rate-limits as an admin endpoint.
 *
 * <p>Rows are read in fixed-size pages ordered by (interruption_date, id) and written straight to
 * the response, so memory stays bounded as the dataset grows and no long-lived read transaction or
 * DB cursor is held while a slow client downloads.
 */
@RestController
@RequestMapping("/api/open-data")
@RequiredArgsConstructor
public class OpenDataController {

    static final int PAGE_SIZE = 500;
    private static final String LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/";
    private static final String BOM = "﻿";

    private final EnelOutageRepository enelOutageRepo;

    @GetMapping("/outages.csv")
    public void outagesCsv(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String format,
            HttpServletResponse response) throws IOException {

        // Validate everything before touching the response so errors are clean 400s.
        OpenDataOutageRow.Variant variant;
        if (format == null || "csv".equals(format)) {
            variant = OpenDataOutageRow.Variant.STANDARD;
        } else if ("excel".equals(format)) {
            variant = OpenDataOutageRow.Variant.EXCEL;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be csv or excel");
        }
        if (month != null && year == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month requires year");
        }
        if (year != null) {
            RequestValidation.requireYear(year);
        }
        if (month != null) {
            RequestValidation.requireMonth(month);
        }

        Function<Pageable, Page<EnelOutage>> reader;
        String scope;
        if (year == null) {
            reader = enelOutageRepo::findAll;
            scope = "all";
        } else if (month == null) {
            reader = p -> enelOutageRepo.findByYear(year, p);
            scope = String.valueOf(year);
        } else {
            reader = p -> enelOutageRepo.findByYearAndMonth(year, month, p);
            scope = "%d-%02d".formatted(year, month);
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
            "attachment; filename=\"sevillasinluz-cortes-" + scope
                + (variant == OpenDataOutageRow.Variant.EXCEL ? "-excel" : "") + ".csv\"");
        response.setHeader("Cache-Control", "public, max-age=300");
        response.setHeader("Link", "<" + LICENSE_URL + ">; rel=\"license\"");

        Writer writer = new BufferedWriter(
            new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        writer.write(BOM);
        writer.write(OpenDataOutageRow.header(variant));
        writer.write("\r\n");

        Pageable pageable = PageRequest.of(0, PAGE_SIZE,
            Sort.by(Sort.Direction.ASC, "interruptionDate").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<EnelOutage> page;
        do {
            page = reader.apply(pageable);
            for (EnelOutage o : page.getContent()) {
                writer.write(OpenDataOutageRow.toCsvRow(o, variant));
                writer.write("\r\n");
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
        writer.flush();
    }
}
