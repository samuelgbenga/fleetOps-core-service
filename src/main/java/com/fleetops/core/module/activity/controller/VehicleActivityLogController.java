package com.fleetops.core.module.activity.controller;

import com.fleetops.core.module.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.module.activity.service.VehicleActivityLogService;
import com.fleetops.core.shared.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Activity Logs")
public class VehicleActivityLogController {

    private final VehicleActivityLogService activityLogService;

    @GetMapping("/api/admin/activity-logs")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Get activity logs for company")
    public ResponseEntity<Page<VehicleActivityLogResponse>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        return ResponseEntity.ok(activityLogService.getLogs(PaginationUtils.of(page, size, sortBy, direction)));
    }

    @GetMapping("/api/platform/activity-logs")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get all activity logs (platform)")
    public ResponseEntity<Page<VehicleActivityLogResponse>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        return ResponseEntity.ok(activityLogService.getAllLogs(PaginationUtils.of(page, size, sortBy, direction)));
    }
}
