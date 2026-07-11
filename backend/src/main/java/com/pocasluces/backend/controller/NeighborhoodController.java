package com.pocasluces.backend.controller;

import com.pocasluces.backend.dto.NeighborhoodResponse;
import com.pocasluces.backend.repository.NeighborhoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/neighborhoods")
@RequiredArgsConstructor
public class NeighborhoodController {

    private final NeighborhoodRepository neighborhoodRepo;

    @GetMapping
    public List<NeighborhoodResponse> getNeighborhoods() {
        return neighborhoodRepo.findAll().stream()
            .map(NeighborhoodResponse::from)
            .toList();
    }
}
