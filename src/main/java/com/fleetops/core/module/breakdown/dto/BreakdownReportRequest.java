package com.fleetops.core.module.breakdown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BreakdownReportRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    private Long tripRequestId;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    private String locationDescription;

    @NotBlank(message = "Description is required")
    private String description;
}
