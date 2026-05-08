package com.fleetops.core.maintenance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveFlagRequest {

    @NotNull(message = "New milestone interval is required")
    @Min(value = 100, message = "Milestone interval must be at least 100 km")
    private Double newMilestoneInterval;

    @NotBlank(message = "Service notes are required")
    private String serviceNotes;
}
