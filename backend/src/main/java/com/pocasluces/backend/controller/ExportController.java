package com.pocasluces.backend.controller;

import com.pocasluces.backend.config.ApiKeyAuth;
import com.pocasluces.backend.controller.validation.RequestValidation;
import com.pocasluces.backend.dto.OutageExportDto;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/outages/export")
@RequiredArgsConstructor
public class ExportController {

    private static final int EXPORT_PAGE_SIZE = 500;

    private final EnelOutageRepository enelOutageRepo;
    private final ApiKeyAuth apiKeyAuth;

    @GetMapping("/csv")
    public void exportCsv(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        apiKeyAuth.requireValidKey(request);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"cortes_endesa.csv\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (PrintWriter writer = response.getWriter()) {
            writer.println(OutageExportDto.header());

            if (year != null && month != null) {
                RequestValidation.requireMonth(month);
                streamPageable(year, month, writer);
            } else if (year != null) {
                streamPageable(year, writer);
            } else {
                streamAll(writer);
            }

            writer.flush();
        }
    }

    private void streamAll(PrintWriter writer) {
        Pageable pageable = PageRequest.of(0, EXPORT_PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "interruptionDate"));
        Page<EnelOutage> page;
        do {
            page = enelOutageRepo.findAll(pageable);
            writePage(page, writer);
            pageable = page.nextPageable();
        } while (page.hasNext());
    }

    private void streamPageable(int year, PrintWriter writer) {
        RequestValidation.requireYear(year);
        Pageable pageable = PageRequest.of(0, EXPORT_PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "interruptionDate"));
        Page<EnelOutage> page;
        do {
            page = enelOutageRepo.findByYear(year, pageable);
            writePage(page, writer);
            pageable = page.nextPageable();
        } while (page.hasNext());
    }

    private void streamPageable(int year, int month, PrintWriter writer) {
        RequestValidation.requireYear(year);
        RequestValidation.requireMonth(month);
        Pageable pageable = PageRequest.of(0, EXPORT_PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "interruptionDate"));
        Page<EnelOutage> page;
        do {
            page = enelOutageRepo.findByYearAndMonth(year, month, pageable);
            writePage(page, writer);
            pageable = page.nextPageable();
        } while (page.hasNext());
    }

    private void writePage(Page<EnelOutage> page, PrintWriter writer) {
        for (EnelOutage o : page.getContent()) {
            writer.println(OutageExportDto.from(o).toCsvRow());
        }
    }
}
