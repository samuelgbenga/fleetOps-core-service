package com.fleetops.core.vehicle.dto;

import com.fleetops.core.validation.ValidPlateNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VehicleRequest {

    @NotBlank(message = "Make is required")
    private String make;

    @NotBlank(message = "Model is required")
    private String model;

    @ValidPlateNumber
    private String plateNumber;

    private Double milestoneInterval;
}
