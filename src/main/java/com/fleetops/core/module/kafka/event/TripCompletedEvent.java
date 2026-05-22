package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TripCompletedEvent {
    private Long tripRequestId;
    private Long companyId;
    private Long vehicleId;
    private String plateNumber;
}
