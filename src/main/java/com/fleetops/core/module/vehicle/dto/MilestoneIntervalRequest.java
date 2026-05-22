package com.fleetops.core.module.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MilestoneIntervalRequest {
    @NotNull @Positive
    private Double milestoneInterval;
}
