package com.fleetops.core.module.maintenance.dto;

import com.fleetops.core.module.maintenance.model.MaintenanceQuotation;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class QuotationResponse {
    private Long id;
    private Long flagId;
    private Long crewId;
    private String crewName;
    private BigDecimal estimatedCost;
    private String description;
    private String partsNeeded;
    private BigDecimal actualCost;
    private String status;
    private String rejectionReason;
    private Integer revisionNumber;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    public static QuotationResponse from(MaintenanceQuotation q) {
        return QuotationResponse.builder()
                .id(q.getId())
                .flagId(q.getFlag().getId())
                .crewId(q.getCrew().getId())
                .crewName(q.getCrew().getName())
                .estimatedCost(q.getEstimatedCost())
                .description(q.getDescription())
                .partsNeeded(q.getPartsNeeded())
                .actualCost(q.getActualCost())
                .status(q.getStatus().name())
                .rejectionReason(q.getRejectionReason())
                .revisionNumber(q.getRevisionNumber())
                .submittedAt(q.getSubmittedAt())
                .reviewedAt(q.getReviewedAt())
                .build();
    }
}
