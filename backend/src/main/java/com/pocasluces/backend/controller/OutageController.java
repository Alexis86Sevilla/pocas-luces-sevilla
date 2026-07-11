package com.pocasluces.backend.controller;

import com.pocasluces.backend.dto.ChartDataPoint;
import com.pocasluces.backend.dto.NeighborhoodStatsDto;
import com.pocasluces.backend.dto.OutageExportDto;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.entity.Neighborhood;
import com.pocasluces.backend.entity.OutageEvent;
import com.pocasluces.backend.entity.Testimonial;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.repository.NeighborhoodRepository;
import com.pocasluces.backend.repository.OutageEventRepository;
import com.pocasluces.backend.repository.TestimonialRepository;
import com.pocasluces.backend.service.OutageDataScheduler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutageController {

    private static final int MAX_PAGE_SIZE = 500;

    private final NeighborhoodRepository neighborhoodRepo;
    private final OutageEventRepository outageRepo;
    private final TestimonialRepository testimonialRepo;
    private final EnelOutageRepository enelOutageRepo;
    private final OutageDataScheduler scheduler;

    // -- Barrios --

    @GetMapping("/neighborhoods")
    public List<Neighborhood> getNeighborhoods() {
        return neighborhoodRepo.findAll();
    }

    // -- Cortes (datos manuales / históricos) --

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

    // -- Estadísticas (datos manuales / históricos) --

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

    // -- Testimonios --

    @GetMapping("/testimonials")
    public List<Testimonial> getTestimonials() {
        return testimonialRepo.findAll();
    }

    // -- Datos en tiempo real (Enel) --

    @GetMapping("/outages/yearly")
    public List<EnelOutage> getOutagesByYear(@RequestParam int year) {
        return enelOutageRepo.findByYear(year);
    }

    @GetMapping("/outages/live")
    public List<EnelOutage> getLiveOutages() {
        LocalDateTime now = LocalDateTime.now();
        return enelOutageRepo.findCurrentlyActive(now, now.minusHours(6));
    }

    @GetMapping("/outages/monthly")
    public List<EnelOutage> getMonthlyOutages(
            @RequestParam int year,
            @RequestParam int month) {
        return enelOutageRepo.findByYearAndMonth(year, month);
    }

    // -- Gráfica (datos agregados por mes y barrio) --

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

    // -- Búsqueda paginada y filtrable de datos Enel --

    @GetMapping("/outages/enel")
    public Page<EnelOutage> getEnelOutages(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "interruptionDate"));

        if (neighborhood != null && !neighborhood.isBlank()) {
            return enelOutageRepo.findByNeighborhoodNameIgnoreCase(neighborhood.trim(), pageable);
        }
        if (year != null && month != null) {
            return enelOutageRepo.findByYearAndMonth(year, month, pageable);
        }
        if (year != null) {
            return enelOutageRepo.findByYear(year, pageable);
        }
        return enelOutageRepo.findAll(pageable);
    }

    // -- Exportación para denuncia / evidencia --

    @GetMapping("/outages/export/csv")
    public void exportCsv(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpServletResponse response) throws IOException {

        List<EnelOutage> outages;
        if (year != null && month != null) {
            outages = enelOutageRepo.findByYearAndMonth(year, month);
        } else if (year != null) {
            outages = enelOutageRepo.findByYear(year);
        } else {
            outages = enelOutageRepo.findAllByOrderByInterruptionDateDesc();
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"cortes_endesa.csv\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (PrintWriter writer = response.getWriter()) {
            writer.println(OutageExportDto.header());
            for (EnelOutage o : outages) {
                writer.println(OutageExportDto.from(o).toCsvRow());
            }
            writer.flush();
        }
    }

    // -- Administración --

    @PostMapping("/outages/fetch")
    public ResponseEntity<String> triggerFetch() {
        scheduler.fetchAndSaveOutages();
        return ResponseEntity.ok("Fetch triggered. Check /api/outages/live");
    }
}
