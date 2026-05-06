package com.fleetops.core.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VehicleRequest {

    @NotBlank(message = "Make is required")
    private String make;

    @NotBlank(message = "Model is required")
    private String model;

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    private Double milestoneInterval; // defaults to 5000 if null
}
