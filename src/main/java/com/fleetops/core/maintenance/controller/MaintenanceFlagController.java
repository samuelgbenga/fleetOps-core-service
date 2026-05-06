package com.fleetops.core.maintenance.controller;

import com.fleetops.core.maintenance.dto.AssignFlagRequest;
import com.fleetops.core.maintenance.dto.MaintenanceFlagResponse;
import com.fleetops.core.maintenance.dto.ProgressUpdateRequest;
import com.fleetops.core.maintenance.service.MaintenanceFlagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance-flags")
@RequiredArgsConstructor
public class MaintenanceFlagController {

    private final MaintenanceFlagService maintenanceFlagService;

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<MaintenanceFlagResponse>> getAll() {
        return ResponseEntity.ok(maintenanceFlagService.getAllFlags());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('MAINTENANCE_TEAM')")
    public ResponseEntity<List<MaintenanceFlagResponse>> getMine() {
        return ResponseEntity.ok(maintenanceFlagService.getMyAssignedFlags());
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public ResponseEntity<MaintenanceFlagResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignFlagRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.assignFlag(id, request));
    }

    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('MAINTENANCE_TEAM')")
    public ResponseEntity<MaintenanceFlagResponse> progress(
            @PathVariable Long id,
            @Valid @RequestBody ProgressUpdateRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.updateProgress(id, request));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MAINTENANCE_TEAM')")
    public ResponseEntity<MaintenanceFlagResponse> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceFlagService.resolveFlag(id));
    }
}
