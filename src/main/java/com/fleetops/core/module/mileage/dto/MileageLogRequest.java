package com.fleetops.core.module.mileage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MileageLogRequest {
    @NotNull
    private Long vehicleId;
    private Long tripRequestId;
    @NotNull @Positive
    private Double reportedMileage;
}
