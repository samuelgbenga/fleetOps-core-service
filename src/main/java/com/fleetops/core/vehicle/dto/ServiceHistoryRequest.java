package com.fleetops.core.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ServiceHistoryRequest {
    @NotBlank(message = "Service history notes are required")
    private String serviceHistory;
}
