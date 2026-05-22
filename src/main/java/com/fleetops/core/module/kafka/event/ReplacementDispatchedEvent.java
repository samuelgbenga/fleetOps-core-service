package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReplacementDispatchedEvent {
    private Long breakdownId;
    private String fieldStaffEmail;
    private String fieldStaffName;
    private String replacementPlateNumber;
}
