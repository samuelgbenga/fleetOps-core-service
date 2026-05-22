package com.fleetops.core.module.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFlagRequest {
    @NotNull
    private Long vehicleId;
    @NotBlank
    private String description;
}
