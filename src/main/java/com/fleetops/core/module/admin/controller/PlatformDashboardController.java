package com.fleetops.core.module.admin.controller;

import com.fleetops.core.module.admin.dto.PlatformDashboardSummaryResponse;
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
@RequestMapping("/api/platform/dashboard")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Platform - Dashboard")
public class PlatformDashboardController {

    private final AdminReportService adminReportService;

    @GetMapping("/summary")
    @Operation(summary = "Get platform dashboard summary")
    public ResponseEntity<PlatformDashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(adminReportService.getPlatformDashboardSummary());
    }
}
