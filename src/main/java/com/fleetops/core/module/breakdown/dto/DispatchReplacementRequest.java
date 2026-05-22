package com.fleetops.core.module.breakdown.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DispatchReplacementRequest {

    @NotNull(message = "Replacement vehicle ID is required")
    private Long replacementVehicleId;
}
