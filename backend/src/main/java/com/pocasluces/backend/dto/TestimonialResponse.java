package com.pocasluces.backend.dto;

import com.pocasluces.backend.entity.Testimonial;

import java.time.LocalDateTime;

public record TestimonialResponse(
    Long id,
    String authorName,
    String embedUrl,
    String platform,
    LocalDateTime createdAt
) {

    public static TestimonialResponse from(Testimonial testimonial) {
        if (testimonial == null) {
            return null;
        }
        return new TestimonialResponse(
            testimonial.getId(),
            testimonial.getAuthorName(),
            testimonial.getEmbedUrl(),
            testimonial.getPlatform(),
            testimonial.getCreatedAt()
        );
    }
}
