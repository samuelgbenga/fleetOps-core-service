package com.fleetops.core.activity.dto;

import com.fleetops.core.activity.entity.VehicleActivityLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VehicleActivityLogResponse {

    private Long id;
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
