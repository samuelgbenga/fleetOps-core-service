package com.fleetops.core.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UtilisationReportResponse {
    private long totalVehicles;
    private long availableVehicles;
    private long assignedVehicles;
    private long maintenanceVehicles;
    private long totalTripsAllTime;
    private long pendingTripRequests;
}
