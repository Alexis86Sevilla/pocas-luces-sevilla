package com.pocasluces.backend.controller;

import com.pocasluces.backend.controller.validation.RequestValidation;
import com.pocasluces.backend.dto.ChartDataPoint;
import com.pocasluces.backend.dto.EnelOutageResponse;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.service.OutageDataScheduler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outages")
@RequiredArgsConstructor
public class EnelOutageController {

    private static final int MAX_PAGE_SIZE = 500;

    private final EnelOutageRepository enelOutageRepo;
    private final OutageDataScheduler scheduler;
    private final com.pocasluces.backend.config.ApiKeyAuth apiKeyAuth;
    private final Clock clock;

    @GetMapping("/yearly")
    public List<EnelOutageResponse> getOutagesByYear(@RequestParam int year) {
        RequestValidation.requireYear(year);
        return enelOutageRepo.findByYear(year).stream()
            .map(EnelOutageResponse::from)
            .toList();
    }

    @GetMapping("/live")
    public List<EnelOutageResponse> getLiveOutages() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime since = now.minusHours(6);
        log.info("Live query: now={}, since={}", now, since);
        List<EnelOutageResponse> result = enelOutageRepo.findCurrentlyActive(now, since).stream()
            .map(EnelOutageResponse::from)
            .toList();
        log.info("Live query result: {} outages", result.size());
        return result;
    }

    @GetMapping("/monthly")
    public List<EnelOutageResponse> getMonthlyOutages(
            @RequestParam int year,
            @RequestParam int month) {
        RequestValidation.requireYear(year);
        RequestValidation.requireMonth(month);
        return enelOutageRepo.findByYearAndMonth(year, month).stream()
            .map(EnelOutageResponse::from)
            .toList();
    }

    @GetMapping("/chart")
    public List<ChartDataPoint> getChartData(@RequestParam int year) {
        RequestValidation.requireYear(year);
        List<Object[]> rows = enelOutageRepo.aggregateByMonthAndNeighborhood(year);
        return rows.stream()
            .map(row -> new ChartDataPoint(
                (Integer) row[0],
                (String) row[1],
                (Long) row[2]
            ))
            .toList();
    }

    @GetMapping("/enel")
    public Page<EnelOutageResponse> getEnelOutages(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int validPage = RequestValidation.requirePage(page);
        int validSize = RequestValidation.requireSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(validPage, validSize,
            Sort.by(Sort.Direction.DESC, "interruptionDate"));

        Page<EnelOutageResponse> result;
        if (neighborhood != null && !neighborhood.isBlank()) {
            result = enelOutageRepo.findByNeighborhoodNameIgnoreCase(neighborhood.trim(), pageable)
                .map(EnelOutageResponse::from);
        } else if (year != null && month != null) {
            RequestValidation.requireMonth(month);
            result = enelOutageRepo.findByYearAndMonth(year, month, pageable)
                .map(EnelOutageResponse::from);
        } else if (year != null) {
            result = enelOutageRepo.findByYear(year, pageable)
                .map(EnelOutageResponse::from);
        } else {
            result = enelOutageRepo.findAll(pageable)
                .map(EnelOutageResponse::from);
        }
        return result;
    }

    @PostMapping("/fetch")
    public ResponseEntity<String> triggerFetch(HttpServletRequest request) {
        apiKeyAuth.requireValidKey(request);
        scheduler.fetchAndSaveOutages();
        return ResponseEntity.ok("Fetch triggered. Check /api/outages/live");
    }
}
