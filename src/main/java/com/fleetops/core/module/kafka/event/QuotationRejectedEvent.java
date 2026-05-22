package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QuotationRejectedEvent {
    private Long flagId;
    private String plateNumber;
    private String crewEmail;
    private String crewName;
    private String rejectionReason;
}
