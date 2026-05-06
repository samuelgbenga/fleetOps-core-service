package com.fleetops.core.triprequest.dto;

import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TripRequestResponse {
    private Long id;
    private Long fieldStaffId;
    private String fieldStaffName;
    private Long vehicleId;
    private String plateNumber;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private TripRequestStatus status;
    private LocalDateTime createdAt;

    public static TripRequestResponse from(TripRequest t) {
        return TripRequestResponse.builder()
                .id(t.getId())
                .fieldStaffId(t.getFieldStaff().getId())
                .fieldStaffName(t.getFieldStaff().getName())
                .vehicleId(t.getVehicle().getId())
                .plateNumber(t.getVehicle().getPlateNumber())
                .destination(t.getDestination())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
