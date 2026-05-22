package com.fleetops.core.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformDashboardSummaryResponse {

    private long totalCompanies;
    private long activeCompanies;
    private long pendingCompanies;
    private long totalVehicles;
    private long totalUsers;
    private long totalOpenFlags;
    private long totalBreakdowns;
    private long totalResolvedFlags;
}
