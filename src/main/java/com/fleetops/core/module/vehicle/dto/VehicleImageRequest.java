package com.fleetops.core.module.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VehicleImageRequest {
    @NotBlank
    private String imageUrl;
    @NotBlank
    private String imageId;
}
