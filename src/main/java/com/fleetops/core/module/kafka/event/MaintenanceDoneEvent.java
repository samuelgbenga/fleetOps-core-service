package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MaintenanceDoneEvent {
    private Long flagId;
    private String plateNumber;
    private String crewName;
    private String fleetManagerEmail;
    private String fleetManagerName;
}
