package com.fleetops.core.module.vehicle.dto;

import com.fleetops.core.module.vehicle.model.VehicleImage;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VehicleImageResponse {
    private Long id;
    private String imageUrl;
    private String imageId;

    public static VehicleImageResponse from(VehicleImage img) {
        return VehicleImageResponse.builder()
                .id(img.getId())
                .imageUrl(img.getImageUrl())
                .imageId(img.getImageId())
                .build();
    }
}
