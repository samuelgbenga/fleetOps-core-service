package com.fleetops.core.module.breakdown.dto;

import com.fleetops.core.module.breakdown.model.BreakdownReport;
import com.fleetops.core.module.breakdown.model.BreakdownStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BreakdownResponse {

    private Long id;
    private Long companyId;
    private Long vehicleId;
    private String vehiclePlateNumber;
    private Long tripRequestId;
    private Long fieldStaffId;
    private String fieldStaffName;
    private Double latitude;
    private Double longitude;
    private String locationDescription;
    private String description;
    private BreakdownStatus status;
    private Long assignedCrewId;
    private String assignedCrewName;
    private Long replacementVehicleId;
    private String replacementVehiclePlate;
    private Long replacementTripRequestId;
    private Long maintenanceFlagId;
    private LocalDateTime reportedAt;
    private LocalDateTime resolvedAt;

    public static BreakdownResponse from(BreakdownReport r) {
        return BreakdownResponse.builder()
                .id(r.getId())
                .companyId(r.getCompany().getId())
                .vehicleId(r.getVehicle().getId())
                .vehiclePlateNumber(r.getVehicle().getPlateNumber())
                .tripRequestId(r.getTripRequest() != null ? r.getTripRequest().getId() : null)
                .fieldStaffId(r.getFieldStaff().getId())
                .fieldStaffName(r.getFieldStaff().getName())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .locationDescription(r.getLocationDescription())
                .description(r.getDescription())
                .status(r.getStatus())
                .assignedCrewId(r.getAssignedCrew() != null ? r.getAssignedCrew().getId() : null)
                .assignedCrewName(r.getAssignedCrew() != null ? r.getAssignedCrew().getName() : null)
                .replacementVehicleId(r.getReplacementVehicle() != null ? r.getReplacementVehicle().getId() : null)
                .replacementVehiclePlate(r.getReplacementVehicle() != null ? r.getReplacementVehicle().getPlateNumber() : null)
                .replacementTripRequestId(r.getReplacementTripRequest() != null ? r.getReplacementTripRequest().getId() : null)
                .maintenanceFlagId(r.getMaintenanceFlag() != null ? r.getMaintenanceFlag().getId() : null)
                .reportedAt(r.getReportedAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
