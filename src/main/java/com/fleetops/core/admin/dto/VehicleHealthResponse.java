package com.fleetops.core.admin.dto;

import com.fleetops.core.vehicle.enums.VehicleStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleHealthResponse {
    private Long vehicleId;
    private String plateNumber;
    private String make;
    private String model;
    private Double currentMileage;
    private Double milestoneInterval;
    private VehicleStatus status;
    private long openMaintenanceFlags;
}
