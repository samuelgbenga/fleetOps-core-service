package com.fleetops.core.vehicle.dto;

import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VehicleResponse {
    private Long id;
    private String make;
    private String model;
    private String plateNumber;
    private Double currentMileage;
    private Double milestoneInterval;
    private VehicleStatus status;
    private Double lifecyclePercentage;
    private Boolean markedForSale;
    private List<MediaResponse> mediaFiles;
    private List<ServiceHistoryResponse> serviceHistories;
    private LocalDateTime registeredAt;

    public static VehicleResponse from(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .make(v.getMake())
                .model(v.getModel())
                .plateNumber(v.getPlateNumber())
                .currentMileage(v.getCurrentMileage())
                .milestoneInterval(v.getMilestoneInterval())
                .status(v.getStatus())
                .lifecyclePercentage(v.getLifecyclePercentage())
                .markedForSale(v.getMarkedForSale())
                .mediaFiles(MediaResponse.fromList(v.getMediaFiles()))
                .serviceHistories(List.of())
                .registeredAt(v.getRegisteredAt())
                .build();
    }

    public static VehicleResponse from(Vehicle v, List<ServiceHistoryResponse> histories) {
        return VehicleResponse.builder()
                .id(v.getId())
                .make(v.getMake())
                .model(v.getModel())
                .plateNumber(v.getPlateNumber())
                .currentMileage(v.getCurrentMileage())
                .milestoneInterval(v.getMilestoneInterval())
                .status(v.getStatus())
                .lifecyclePercentage(v.getLifecyclePercentage())
                .markedForSale(v.getMarkedForSale())
                .mediaFiles(MediaResponse.fromList(v.getMediaFiles()))
                .serviceHistories(histories)
                .registeredAt(v.getRegisteredAt())
                .build();
    }
}
