package com.pocasluces.backend.controller;

import com.pocasluces.backend.dto.ChartDataPoint;
import com.pocasluces.backend.dto.NeighborhoodStatsDto;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.entity.Neighborhood;
import com.pocasluces.backend.service.OutageDataScheduler;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.entity.OutageEvent;
import com.pocasluces.backend.entity.Testimonial;
import com.pocasluces.backend.repository.NeighborhoodRepository;
import com.pocasluces.backend.repository.OutageEventRepository;
import com.pocasluces.backend.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutageController {

    private final NeighborhoodRepository neighborhoodRepo;
    private final OutageEventRepository outageRepo;
    private final TestimonialRepository testimonialRepo;
    private final EnelOutageRepository enelOutageRepo;
    private final OutageDataScheduler scheduler;

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
        return outageRepo.findAllWithNeighborhood();
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

    // ── Datos en tiempo real (Enel) ──

    @GetMapping("/outages/yearly")
    public List<EnelOutage> getOutagesByYear(@RequestParam int year) {
        return enelOutageRepo.findByYear(year);
    }

    @GetMapping("/outages/live")
    public List<EnelOutage> getLiveOutages() {
        return enelOutageRepo.findCurrentlyActive(LocalDateTime.now().minusHours(6));
    }

    // ── Gráfica (datos agregados por mes y barrio) ──

    @GetMapping("/outages/chart")
    public List<ChartDataPoint> getChartData(@RequestParam int year) {
        List<Object[]> rows = enelOutageRepo.aggregateByMonthAndNeighborhood(year);
        return rows.stream()
            .map(row -> new ChartDataPoint(
                (Integer) row[0],
                (String) row[1],
                (Long) row[2]
            ))
            .toList();
    }

    // ── Detalle mensual (datos reales de Enel) ──

    @GetMapping("/outages/monthly")
    public List<EnelOutage> getMonthlyOutages(
            @RequestParam int year,
            @RequestParam int month) {
        return enelOutageRepo.findByYearAndMonth(year, month);
    }

    @PostMapping("/outages/fetch")
    public String triggerFetch() {
        scheduler.fetchAndSaveOutages();
        return "Fetch triggered. Check /api/outages/live";
    }
}
