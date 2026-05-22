package com.fleetops.core.module.mileage.dto;

import com.fleetops.core.module.mileage.model.MileageLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MileageLogResponse {
    private Long id;
    private Long vehicleId;
    private String plateNumber;
    private Long submittedById;
    private String submittedByName;
    private Long tripRequestId;
    private Double reportedMileage;
    private LocalDateTime loggedAt;

    public static MileageLogResponse from(MileageLog m) {
        return MileageLogResponse.builder()
                .id(m.getId())
                .vehicleId(m.getVehicle().getId())
                .plateNumber(m.getVehicle().getPlateNumber())
                .submittedById(m.getSubmittedBy().getId())
                .submittedByName(m.getSubmittedBy().getName())
                .tripRequestId(m.getTripRequest() != null ? m.getTripRequest().getId() : null)
                .reportedMileage(m.getReportedMileage())
                .loggedAt(m.getLoggedAt())
                .build();
    }
}
