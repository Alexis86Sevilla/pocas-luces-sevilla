package com.pocasluces.backend.controller;

import com.pocasluces.backend.dto.NeighborhoodStatsDto;
import com.pocasluces.backend.entity.Neighborhood;
import com.pocasluces.backend.entity.OutageEvent;
import com.pocasluces.backend.entity.Testimonial;
import com.pocasluces.backend.repository.NeighborhoodRepository;
import com.pocasluces.backend.repository.OutageEventRepository;
import com.pocasluces.backend.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Ajustar en producción
@RequiredArgsConstructor
public class OutageController {

    private final NeighborhoodRepository neighborhoodRepo;
    private final OutageEventRepository outageRepo;
    private final TestimonialRepository testimonialRepo;

    // ── Barrios ──

    @GetMapping("/neighborhoods")
    public List<Neighborhood> getNeighborhoods() {
        return neighborhoodRepo.findAll();
    }

    // ── Cortes ──

    @GetMapping("/outages")
    public List<OutageEvent> getOutages(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        if (year != null && month != null) {
            return outageRepo.findByYearAndMonth(year, month);
        }
        if (year != null) {
            return outageRepo.findByYear(year);
        }
        return outageRepo.findAll();
    }

    // ── Estadísticas ──

    @GetMapping("/stats")
    public List<NeighborhoodStatsDto> getStats(@RequestParam int year) {
        List<Object[]> rows = outageRepo.findStatsByYear(year);
        return rows.stream().map(row -> {
            Long neighborhoodId = (Long) row[0];
            long count = (Long) row[1];
            double avg = (Double) row[2];

            Neighborhood n = neighborhoodRepo.findById(neighborhoodId).orElse(null);
            return new NeighborhoodStatsDto(
                neighborhoodId,
                n != null ? n.getName() : "Desconocido",
                count,
                Math.round(avg * 10.0) / 10.0,
                n != null ? n.getCategory() : ""
            );
        }).toList();
    }

    // ── Testimonios ──

    @GetMapping("/testimonials")
    public List<Testimonial> getTestimonials() {
        return testimonialRepo.findAll();
    }
}
