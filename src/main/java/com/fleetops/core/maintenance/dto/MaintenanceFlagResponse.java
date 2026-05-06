package com.fleetops.core.maintenance.dto;

import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.enums.FlagStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceFlagResponse {
    private Long id;
    private Long vehicleId;
    private String plateNumber;
    private Long assignedToId;
    private String assignedToName;
    private Long assignedById;
    private String assignedByName;
    private Double mileageAtTrigger;
    private FlagStatus status;
    private String progressNotes;
    private LocalDateTime triggeredAt;
    private LocalDateTime assignedAt;
    private LocalDateTime resolvedAt;

    public static MaintenanceFlagResponse from(MaintenanceFlag f) {
        return MaintenanceFlagResponse.builder()
                .id(f.getId())
                .vehicleId(f.getVehicle().getId())
                .plateNumber(f.getVehicle().getPlateNumber())
                .assignedToId(f.getAssignedTo() != null ? f.getAssignedTo().getId() : null)
                .assignedToName(f.getAssignedTo() != null ? f.getAssignedTo().getName() : null)
                .assignedById(f.getAssignedBy() != null ? f.getAssignedBy().getId() : null)
                .assignedByName(f.getAssignedBy() != null ? f.getAssignedBy().getName() : null)
                .mileageAtTrigger(f.getMileageAtTrigger())
                .status(f.getStatus())
                .progressNotes(f.getProgressNotes())
                .triggeredAt(f.getTriggeredAt())
                .assignedAt(f.getAssignedAt())
                .resolvedAt(f.getResolvedAt())
                .build();
    }
}
