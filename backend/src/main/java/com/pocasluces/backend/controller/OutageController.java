package com.pocasluces.backend.controller;

import com.pocasluces.backend.controller.validation.RequestValidation;
import com.pocasluces.backend.dto.DistrictStatsDto;
import com.pocasluces.backend.dto.OutageEventResponse;
import com.pocasluces.backend.repository.OutageEventRepository;
import com.pocasluces.backend.service.DistrictLocator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutageController {

    private final OutageEventRepository outageRepo;
    private final DistrictLocator districtLocator;

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
    public List<DistrictStatsDto> getStats(@RequestParam int year) {
        RequestValidation.requireYear(year);
        List<Object[]> rows = outageRepo.findStatsByYear(year);

        Map<String, DistrictAccumulator> grouped = rows.stream()
            .collect(Collectors.toMap(
                row -> districtLocator.findDistrictByNeighborhoodName((String) row[1]),
                row -> new DistrictAccumulator((Long) row[2], (Double) row[3]),
                DistrictAccumulator::merge
            ));

        return grouped.entrySet().stream()
            .map(e -> new DistrictStatsDto(
                e.getKey(),
                e.getValue().count,
                Math.round(e.getValue().averageMinutes() * 10.0) / 10.0
            ))
            .sorted(Comparator.comparingLong(DistrictStatsDto::getOutageCount).reversed()
                .thenComparing(DistrictStatsDto::getDistrictName))
            .toList();
    }

    private static class DistrictAccumulator {
        private long count;
        private double totalMinutes;

        DistrictAccumulator(long count, double averageMinutes) {
            this.count = count;
            this.totalMinutes = averageMinutes * count;
        }

        DistrictAccumulator merge(DistrictAccumulator other) {
            this.count += other.count;
            this.totalMinutes += other.totalMinutes;
            return this;
        }

        double averageMinutes() {
            return count == 0 ? 0.0 : totalMinutes / count;
        }
    }
}
