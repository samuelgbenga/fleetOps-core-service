package com.fleetops.core.module.lga.model;

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
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String lga;

    @Column(nullable = false, length = 100)
    private String state;
}
