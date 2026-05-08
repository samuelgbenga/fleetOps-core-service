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
    private Long submittedById;
    private String submittedByName;
    private Double mileageAdded;
    private Double newTotalMileage;
    private LocalDateTime loggedAt;

    public static MileageLogResponse from(MileageLog log, Double newTotal) {
        return MileageLogResponse.builder()
                .id(log.getId())
                .vehicleId(log.getVehicle().getId())
                .plateNumber(log.getVehicle().getPlateNumber())
                .submittedById(log.getSubmittedBy().getId())
                .submittedByName(log.getSubmittedBy().getName())
                .mileageAdded(log.getMileageAdded())
                .newTotalMileage(newTotal)
                .loggedAt(log.getLoggedAt())
                .build();
    }

    public static MileageLogResponse from(MileageLog log) {
        return from(log, log.getMileageAfter());
    }
}
