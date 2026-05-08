package com.fleetops.core.vehicle.dto;

import com.fleetops.core.vehicle.entity.ServiceHistory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ServiceHistoryResponse {
    private Long id;
    private String fleetManagerName;
    private String notes;
    private Double newMilestoneInterval;
    private LocalDateTime servicedAt;

    public static ServiceHistoryResponse from(ServiceHistory s) {
        return ServiceHistoryResponse.builder()
                .id(s.getId())
                .fleetManagerName(s.getFleetManagerName())
                .notes(s.getNotes())
                .newMilestoneInterval(s.getNewMilestoneInterval())
                .servicedAt(s.getServicedAt())
                .build();
    }
}
