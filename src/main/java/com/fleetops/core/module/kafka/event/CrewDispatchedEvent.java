package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CrewDispatchedEvent {
    private Long breakdownId;
    private String plateNumber;
    private String crewEmail;
    private String crewName;
    private Double latitude;
    private Double longitude;
}
