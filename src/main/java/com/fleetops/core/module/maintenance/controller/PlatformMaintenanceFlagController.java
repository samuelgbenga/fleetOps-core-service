package com.fleetops.core.module.maintenance.controller;

import com.fleetops.core.module.maintenance.dto.AssignCrewRequest;
import com.fleetops.core.module.maintenance.dto.MaintenanceFlagResponse;
import com.fleetops.core.module.maintenance.service.MaintenanceFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/maintenance/flags")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Platform - Maintenance Flags")
public class PlatformMaintenanceFlagController {

    private final MaintenanceFlagService maintenanceFlagService;

    @GetMapping
    @Operation(summary = "List all maintenance flags (platform)")
    public ResponseEntity<List<MaintenanceFlagResponse>> getAll() {
        return ResponseEntity.ok(maintenanceFlagService.getAllPlatform());
    }

    @PatchMapping("/{id}/assign-crew")
    @Operation(summary = "Assign crew to a maintenance flag")
    public ResponseEntity<MaintenanceFlagResponse> assignCrew(@PathVariable Long id,
                                                               @Valid @RequestBody AssignCrewRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.assignCrew(id, request));
    }
}
