package com.fleetops.core.vehicle.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lga_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LgaCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 3)
    private String code;

    @Column(nullable = false)
    private String lga;

    @Column(nullable = false)
    private String state;
}
