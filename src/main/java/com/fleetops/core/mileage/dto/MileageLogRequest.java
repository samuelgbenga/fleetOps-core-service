package com.fleetops.core.mileage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MileageLogRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotNull(message = "Mileage added is required")
    @Positive(message = "Mileage added must be greater than 0")
    private Double mileageAdded;
}
