package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QuotationSubmittedEvent {
    private Long flagId;
    private Long quotationId;
    private String plateNumber;
    private String fleetManagerEmail;
    private String fleetManagerName;
    private BigDecimal estimatedCost;
}
