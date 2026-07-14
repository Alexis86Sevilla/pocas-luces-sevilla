package com.pocasluces.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "neighborhoods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Neighborhood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "postal_code", length = 5)
    private String postalCode;
}
