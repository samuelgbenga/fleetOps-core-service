package com.fleetops.core.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceFlagCreatedEvent {
    private Long vehicleId;
    private String plateNumber;
    private Double mileageAtTrigger;
    private Long fleetManagerId;
    private String fleetManagerEmail;
    private String fleetManagerName;
    private LocalDateTime occurredAt;
}
