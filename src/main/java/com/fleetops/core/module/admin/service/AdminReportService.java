package com.fleetops.core.module.admin.service;

import com.fleetops.core.module.admin.dto.PlatformDashboardSummaryResponse;
import com.fleetops.core.module.admin.dto.UtilisationReportResponse;
import com.fleetops.core.module.admin.dto.VehicleHealthReportResponse;


public interface AdminReportService {

    PlatformDashboardSummaryResponse getPlatformDashboardSummary();

    UtilisationReportResponse getUtilisationReport();

    VehicleHealthReportResponse getVehicleHealthReport();
}
