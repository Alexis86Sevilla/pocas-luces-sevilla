package com.pocasluces.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "testimonials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Testimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "embed_url", nullable = false, columnDefinition = "TEXT")
    private String embedUrl;

    @Column(length = 20)
    private String platform; // youtube, instagram

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
