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
    private Double reportedMileage;
    private LocalDateTime loggedAt;

    public static MileageLogResponse from(MileageLog log) {
        return MileageLogResponse.builder()
                .id(log.getId())
                .vehicleId(log.getVehicle().getId())
                .plateNumber(log.getVehicle().getPlateNumber())
                .submittedById(log.getSubmittedBy().getId())
                .submittedByName(log.getSubmittedBy().getName())
                .reportedMileage(log.getReportedMileage())
                .loggedAt(log.getLoggedAt())
                .build();
    }
}
