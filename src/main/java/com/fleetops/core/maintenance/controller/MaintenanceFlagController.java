package com.fleetops.core.maintenance.controller;

import com.fleetops.core.maintenance.dto.ApproveFlagRequest;
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

    /** Fleet manager assigns an OPEN flag to a maintenance team member */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceFlagResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignFlagRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.assignFlag(id, request));
    }

    /** Maintenance team updates progress notes — moves flag to IN_PROGRESS */
    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('MAINTENANCE_TEAM')")
    public ResponseEntity<MaintenanceFlagResponse> progress(
            @PathVariable Long id,
            @Valid @RequestBody ProgressUpdateRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.updateProgress(id, request));
    }

    /** Maintenance team signals work is done — moves flag to PENDING_APPROVAL and notifies fleet manager */
    @PatchMapping("/{id}/done")
    @PreAuthorize("hasRole('MAINTENANCE_TEAM')")
    public ResponseEntity<MaintenanceFlagResponse> markDone(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceFlagService.markWorkDone(id));
    }

    /** Fleet manager approves maintenance — requires new milestone interval + service notes */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceFlagResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveFlagRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.approveMaintenance(id, request));
    }
}
