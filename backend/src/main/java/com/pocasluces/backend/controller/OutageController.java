package com.pocasluces.backend.controller;

import com.pocasluces.backend.controller.validation.RequestValidation;
import com.pocasluces.backend.dto.NeighborhoodStatsDto;
import com.pocasluces.backend.dto.OutageEventResponse;
import com.pocasluces.backend.repository.OutageEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutageController {

    private final OutageEventRepository outageRepo;

    @GetMapping("/outages")
    public List<OutageEventResponse> getOutages(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        if (year != null && month != null) {
            RequestValidation.requireYear(year);
            RequestValidation.requireMonth(month);
            return outageRepo.findByYearAndMonth(year, month).stream()
                .map(OutageEventResponse::from)
                .toList();
        }
        if (year != null) {
            RequestValidation.requireYear(year);
            return outageRepo.findByYear(year).stream()
                .map(OutageEventResponse::from)
                .toList();
        }
        return outageRepo.findAllWithNeighborhood().stream()
            .map(OutageEventResponse::from)
            .toList();
    }

    @GetMapping("/stats")
    public List<NeighborhoodStatsDto> getStats(@RequestParam int year) {
        RequestValidation.requireYear(year);
        List<Object[]> rows = outageRepo.findStatsByYear(year);
        return rows.stream().map(row -> new NeighborhoodStatsDto(
            (Long) row[0],
            (String) row[1],
            (Long) row[3],
            Math.round((Double) row[4] * 10.0) / 10.0,
            (String) row[2]
        )).toList();
    }
}
