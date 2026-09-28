package com.pocasluces.backend.controller;

import com.pocasluces.backend.controller.validation.EmbedUrlValidation;
import com.pocasluces.backend.dto.TestimonialResponse;
import com.pocasluces.backend.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/testimonials")
@RequiredArgsConstructor
public class TestimonialController {

    private final TestimonialRepository testimonialRepo;

    @GetMapping
    public List<TestimonialResponse> getTestimonials() {
        return testimonialRepo.findAll().stream()
            .map(TestimonialResponse::from)
            .filter(this::hasAllowedEmbedUrl)
            .toList();
    }

    private boolean hasAllowedEmbedUrl(TestimonialResponse testimonial) {
        if (EmbedUrlValidation.isAllowed(testimonial.embedUrl())) {
            return true;
        }
        log.warn("Dropping testimonial id={} with disallowed embedUrl host", testimonial.id());
        return false;
    }
}
