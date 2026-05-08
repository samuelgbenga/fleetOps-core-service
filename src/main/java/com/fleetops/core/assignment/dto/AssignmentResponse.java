package com.fleetops.core.assignment.dto;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AssignmentResponse {
    private Long id;
    private Long vehicleId;
    private String plateNumber;
    private Long tripRequestId;
    private Long fieldStaffId;
    private String fieldStaffName;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime assignedAt;

    public static AssignmentResponse from(VehicleAssignment a) {
        return AssignmentResponse.builder()
                .id(a.getId())
                .vehicleId(a.getVehicle().getId())
                .plateNumber(a.getVehicle().getPlateNumber())
                .tripRequestId(a.getTripRequest().getId())
                .fieldStaffId(a.getTripRequest().getFieldStaff().getId())
                .fieldStaffName(a.getTripRequest().getFieldStaff().getName())
                .destination(a.getTripRequest().getDestination())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .assignedAt(a.getAssignedAt())
                .build();
    }
}
