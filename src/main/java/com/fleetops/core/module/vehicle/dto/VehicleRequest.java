package com.fleetops.core.module.vehicle.dto;

import com.fleetops.core.shared.validation.ValidPlateNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VehicleRequest {
    @NotBlank
    private String make;
    @NotBlank
    private String model;
    @NotBlank @ValidPlateNumber
    private String plateNumber;
    private Double milestoneInterval;
    private BigDecimal purchasePrice;
    private LocalDate purchaseDate;
}
