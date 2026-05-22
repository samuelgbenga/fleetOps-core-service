package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MaintenanceProgressEvent {
    private Long flagId;
    private String plateNumber;
    private String progressNotes;
    private String fleetManagerEmail;
    private String fleetManagerName;
}
