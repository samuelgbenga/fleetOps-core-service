package com.fleetops.core.vehicle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MilestoneIntervalRequest {

    @NotNull(message = "Milestone interval is required")
    @Min(value = 100, message = "Milestone interval must be at least 100 km")
    private Double milestoneInterval;
}
