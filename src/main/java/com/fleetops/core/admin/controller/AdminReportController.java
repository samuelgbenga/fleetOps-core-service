package com.fleetops.core.admin.controller;

import com.fleetops.core.admin.dto.UtilisationReportResponse;
import com.fleetops.core.admin.dto.VehicleHealthResponse;
import com.fleetops.core.admin.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping("/utilisation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtilisationReportResponse> getUtilisation() {
        return ResponseEntity.ok(adminReportService.getUtilisationReport());
    }

    @GetMapping("/vehicle-health")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<List<VehicleHealthResponse>> getVehicleHealth() {
        return ResponseEntity.ok(adminReportService.getVehicleHealthSummary());
    }
}
