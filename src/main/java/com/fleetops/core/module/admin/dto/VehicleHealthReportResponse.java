package com.fleetops.core.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleHealthReportResponse {

    private Long companyId;
    private String companyName;
    private long totalVehicles;
    private double avgHealthScore;
    private long criticalCount;
    private long poorCount;
    private long fairCount;
    private long goodCount;
    private long excellentCount;
    private long markedForSaleCount;
}
