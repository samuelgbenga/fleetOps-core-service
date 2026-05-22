package com.fleetops.core.module.activity.dto;

import com.fleetops.core.module.activity.model.VehicleActivityLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VehicleActivityLogResponse {

    private Long id;
    private Long companyId;
    private Long vehicleId;
    private String plateNumber;
    private String eventType;
    private String description;
    private String actorName;
    private String actorRole;
    private LocalDateTime occurredAt;

    public static VehicleActivityLogResponse from(VehicleActivityLog log) {
        return VehicleActivityLogResponse.builder()
                .id(log.getId())
                .companyId(log.getCompany().getId())
                .vehicleId(log.getVehicleId())
                .plateNumber(log.getPlateNumber())
                .eventType(log.getEventType())
                .description(log.getDescription())
                .actorName(log.getActorName())
                .actorRole(log.getActorRole())
                .occurredAt(log.getOccurredAt())
                .build();
    }
}
