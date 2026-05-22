package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TripRejectedEvent {
    private Long tripRequestId;
    private Long companyId;
    private String requesterEmail;
    private String requesterName;
    private String rejectionReason;
}
