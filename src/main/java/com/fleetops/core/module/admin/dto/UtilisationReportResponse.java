package com.fleetops.core.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UtilisationReportResponse {

    private Long companyId;
    private String companyName;
    private long totalVehicles;
    private long availableVehicles;
    private long assignedVehicles;
    private long maintenanceVehicles;
    private long totalTrips;
    private long completedTrips;
    private double utilizationRate;
}
