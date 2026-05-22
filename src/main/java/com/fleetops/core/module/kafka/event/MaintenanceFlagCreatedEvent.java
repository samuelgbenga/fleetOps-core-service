package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MaintenanceFlagCreatedEvent {
    private Long flagId;
    private Long vehicleId;
    private Long companyId;
    private String plateNumber;
    private String triggerType;
    private String description;
}
