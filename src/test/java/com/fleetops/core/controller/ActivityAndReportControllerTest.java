package com.fleetops.core.controller;

import com.fleetops.core.module.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.module.admin.dto.PlatformDashboardSummaryResponse;
import com.fleetops.core.module.admin.dto.UtilisationReportResponse;
import com.fleetops.core.module.admin.dto.VehicleHealthReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ActivityAndReportControllerTest extends BaseControllerIntegrationTest {

    private VehicleActivityLogResponse sampleLog() {
        return VehicleActivityLogResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .eventType("TRIP_STARTED").description("Trip to Lagos started")
                .occurredAt(LocalDateTime.now()).build();
    }

    // ── VehicleActivityLogController ───────────────────────────────────────

    // GET /api/admin/activity-logs

    @Test
    void getActivityLogs_fleetManager_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleLog()), PageRequest.of(0, 20), 1L);
        when(vehicleActivityLogService.getLogs(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getActivityLogs_companyAdmin_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleLog()), PageRequest.of(0, 20), 1L);
        when(vehicleActivityLogService.getLogs(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getActivityLogs_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/activity-logs")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getActivityLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/activity-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getActivityLogs_withPaginationParams_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleLog()), PageRequest.of(0, 20), 1L);
        when(vehicleActivityLogService.getLogs(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs")
                        .header("Authorization", fleetManagerToken)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "occurredAt")
                        .param("direction", "ASC"))
                .andExpect(status().isOk());
    }

    // GET /api/platform/activity-logs

    @Test
    void getAllActivityLogs_platformAdmin_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleLog()), PageRequest.of(0, 20), 1L);
        when(vehicleActivityLogService.getAllLogs(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/platform/activity-logs")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("TRIP_STARTED"));
    }

    @Test
    void getAllActivityLogs_companyAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/activity-logs")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllActivityLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/platform/activity-logs"))
                .andExpect(status().isUnauthorized());
    }

    // ── AdminReportController ──────────────────────────────────────────────

    // GET /api/admin/reports/utilisation

    @Test
    void getUtilisationReport_fleetManager_returns200() throws Exception {
        var report = UtilisationReportResponse.builder()
                .companyId(COMPANY_ID).companyName("Test Co")
                .totalVehicles(5).availableVehicles(3).assignedVehicles(2)
                .totalTrips(20).completedTrips(18).utilizationRate(0.6).build();
        when(adminReportService.getUtilisationReport()).thenReturn(report);

        mockMvc.perform(get("/api/admin/reports/utilisation")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(5));
    }

    @Test
    void getUtilisationReport_companyAdmin_returns200() throws Exception {
        when(adminReportService.getUtilisationReport()).thenReturn(
                UtilisationReportResponse.builder().companyId(COMPANY_ID).build());

        mockMvc.perform(get("/api/admin/reports/utilisation")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getUtilisationReport_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/reports/utilisation")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUtilisationReport_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/reports/utilisation"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/admin/reports/vehicle-health

    @Test
    void getVehicleHealthReport_fleetManager_returns200() throws Exception {
        var report = VehicleHealthReportResponse.builder()
                .companyId(COMPANY_ID).companyName("Test Co")
                .totalVehicles(5).avgHealthScore(85.0)
                .excellentCount(2).goodCount(2).fairCount(1).build();
        when(adminReportService.getVehicleHealthReport()).thenReturn(report);

        mockMvc.perform(get("/api/admin/reports/vehicle-health")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(5));
    }

    @Test
    void getVehicleHealthReport_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/reports/vehicle-health")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isForbidden());
    }

    // ── PlatformDashboardController ────────────────────────────────────────

    // GET /api/platform/dashboard/summary

    @Test
    void getPlatformDashboardSummary_platformAdmin_returns200() throws Exception {
        var summary = PlatformDashboardSummaryResponse.builder()
                .totalCompanies(10).activeCompanies(8).pendingCompanies(2)
                .totalVehicles(50).totalUsers(100).totalOpenFlags(5)
                .totalBreakdowns(3).totalResolvedFlags(45).build();
        when(adminReportService.getPlatformDashboardSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/platform/dashboard/summary")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompanies").value(10))
                .andExpect(jsonPath("$.activeCompanies").value(8));
    }

    @Test
    void getPlatformDashboardSummary_companyAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/dashboard/summary")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPlatformDashboardSummary_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/dashboard/summary")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPlatformDashboardSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/platform/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }
}
