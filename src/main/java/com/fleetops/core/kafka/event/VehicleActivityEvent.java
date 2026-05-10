package com.fleetops.core.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleActivityEvent {

    private String eventType;
    private Long vehicleId;
    private String plateNumber;
    private String description;
    private String actorName;
    private String actorRole;
    private LocalDateTime occurredAt;
}
