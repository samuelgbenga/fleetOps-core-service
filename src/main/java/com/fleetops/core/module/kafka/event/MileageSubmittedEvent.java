package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MileageSubmittedEvent {
    private Long vehicleId;
    private Long companyId;
    private String plateNumber;
    private Double reportedMileage;
    private String submittedByName;
}
