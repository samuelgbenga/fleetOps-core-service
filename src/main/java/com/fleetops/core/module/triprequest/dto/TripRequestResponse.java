package com.fleetops.core.module.triprequest.dto;

import com.fleetops.core.module.triprequest.model.TripRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class TripRequestResponse {
    private Long id;
    private Long companyId;
    private Long vehicleId;
    private String plateNumber;
    private Long requestedById;
    private String requestedByName;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;

    public static TripRequestResponse from(TripRequest t) {
        return TripRequestResponse.builder()
                .id(t.getId())
                .companyId(t.getCompany().getId())
                .vehicleId(t.getVehicle().getId())
                .plateNumber(t.getVehicle().getPlateNumber())
                .requestedById(t.getRequestedBy().getId())
                .requestedByName(t.getRequestedBy().getName())
                .destination(t.getDestination())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .status(t.getStatus().name())
                .rejectionReason(t.getRejectionReason())
                .createdAt(t.getCreatedAt())
                .approvedAt(t.getApprovedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
