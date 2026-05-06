package com.fleetops.core.vehicle.dto;

import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
    private String serviceHistory;
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
                .serviceHistory(v.getServiceHistory())
                .registeredAt(v.getRegisteredAt())
                .build();
    }
}
