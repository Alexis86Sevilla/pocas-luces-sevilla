package com.pocasluces.backend.controller;

import com.pocasluces.backend.dto.TestimonialResponse;
import com.pocasluces.backend.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/testimonials")
@RequiredArgsConstructor
public class TestimonialController {

    private final TestimonialRepository testimonialRepo;

    @GetMapping
    public List<TestimonialResponse> getTestimonials() {
        return testimonialRepo.findAll().stream()
            .map(TestimonialResponse::from)
            .toList();
    }
}
