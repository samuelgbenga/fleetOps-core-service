package com.fleetops.core.module.maintenance.dto;

import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.maintenance.model.MaintenanceQuotation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class MaintenanceFlagResponse {
    private Long id;
    private Long companyId;
    private Long vehicleId;
    private String plateNumber;
    private Long assignedCrewId;
    private String assignedCrewName;
    private String triggerType;
    private String status;
    private String description;
    private String progressNotes;
    private LocalDateTime openedAt;
    private LocalDateTime assignedAt;
    private LocalDateTime resolvedAt;
    private QuotationResponse latestQuotation;

    public static MaintenanceFlagResponse from(MaintenanceFlag f) {
        return MaintenanceFlagResponse.builder()
                .id(f.getId())
                .companyId(f.getCompany().getId())
                .vehicleId(f.getVehicle().getId())
                .plateNumber(f.getVehicle().getPlateNumber())
                .assignedCrewId(f.getAssignedCrew() != null ? f.getAssignedCrew().getId() : null)
                .assignedCrewName(f.getAssignedCrew() != null ? f.getAssignedCrew().getName() : null)
                .triggerType(f.getTriggerType().name())
                .status(f.getStatus().name())
                .description(f.getDescription())
                .progressNotes(f.getProgressNotes())
                .openedAt(f.getOpenedAt())
                .assignedAt(f.getAssignedAt())
                .resolvedAt(f.getResolvedAt())
                .build();
    }

    public static MaintenanceFlagResponse from(MaintenanceFlag f, MaintenanceQuotation latestQuotation) {
        return from(f).toBuilder()
                .latestQuotation(latestQuotation != null ? QuotationResponse.from(latestQuotation) : null)
                .build();
    }
}
