package com.fleetops.core.module.triprequest.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TripRequestCreate {
    @NotNull
    private Long vehicleId;
    @NotBlank
    private String destination;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
}
