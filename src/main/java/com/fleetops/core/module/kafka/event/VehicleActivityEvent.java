package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VehicleActivityEvent {
    private String eventType;
    private Long vehicleId;
    private Long companyId;
    private String plateNumber;
    private String description;
    private String actorName;
    private String actorRole;
    private LocalDateTime occurredAt;
}
