package com.fleetops.core.module.admin.controller;

import com.fleetops.core.module.admin.dto.UtilisationReportResponse;
import com.fleetops.core.module.admin.dto.VehicleHealthReportResponse;
import com.fleetops.core.module.admin.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Admin Reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping("/utilisation")
    @Operation(summary = "Get vehicle utilisation report for the authenticated company")
    public ResponseEntity<UtilisationReportResponse> getUtilisation() {
        return ResponseEntity.ok(adminReportService.getUtilisationReport());
    }

    @GetMapping("/vehicle-health")
    @Operation(summary = "Get vehicle health report for the authenticated company")
    public ResponseEntity<VehicleHealthReportResponse> getVehicleHealth() {
        return ResponseEntity.ok(adminReportService.getVehicleHealthReport());
    }
}
