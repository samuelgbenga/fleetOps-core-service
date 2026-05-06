package com.fleetops.core.mileage.dto;

import com.fleetops.core.mileage.entity.MileageLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MileageLogResponse {
    private Long id;
    private Long vehicleId;
    private String plateNumber;
    private Double mileageAdded;
    private Double newTotalMileage;
    private LocalDateTime loggedAt;

    public static MileageLogResponse from(MileageLog log, Double newTotal) {
        return MileageLogResponse.builder()
                .id(log.getId())
                .vehicleId(log.getVehicle().getId())
                .plateNumber(log.getVehicle().getPlateNumber())
                .mileageAdded(log.getMileageAdded())
                .newTotalMileage(newTotal)
                .loggedAt(log.getLoggedAt())
                .build();
    }
}
