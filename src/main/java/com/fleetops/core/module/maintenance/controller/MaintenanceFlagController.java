package com.fleetops.core.module.maintenance.controller;

import com.fleetops.core.module.maintenance.dto.*;
import com.fleetops.core.module.maintenance.service.MaintenanceFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance/flags")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Maintenance Flags")
public class MaintenanceFlagController {

    private final MaintenanceFlagService maintenanceFlagService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Create a maintenance flag")
    public ResponseEntity<MaintenanceFlagResponse> create(@Valid @RequestBody CreateFlagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceFlagService.createFlag(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "List all maintenance flags for company")
    public ResponseEntity<List<MaintenanceFlagResponse>> getAll() {
        return ResponseEntity.ok(maintenanceFlagService.getAll());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('MAINTENANCE_CREW')")
    @Operation(summary = "Get flags assigned to me (crew)")
    public ResponseEntity<List<MaintenanceFlagResponse>> getMyCrew() {
        return ResponseEntity.ok(maintenanceFlagService.getMyCrew());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN','MAINTENANCE_CREW')")
    @Operation(summary = "Get a maintenance flag by ID")
    public ResponseEntity<MaintenanceFlagResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceFlagService.getById(id));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Get maintenance flags for a vehicle")
    public ResponseEntity<List<MaintenanceFlagResponse>> getByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(maintenanceFlagService.getByVehicle(vehicleId));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Assign maintenance crew to an open flag")
    public ResponseEntity<MaintenanceFlagResponse> assign(@PathVariable Long id,
                                                          @Valid @RequestBody AssignCrewRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.assignCrew(id, request));
    }

    @PostMapping("/{id}/quotation")
    @PreAuthorize("hasRole('MAINTENANCE_CREW')")
    @Operation(summary = "Submit a quotation")
    public ResponseEntity<QuotationResponse> submitQuotation(@PathVariable Long id,
                                                              @Valid @RequestBody QuotationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceFlagService.submitQuotation(id, request));
    }

    @PatchMapping("/{id}/quotation/revise")
    @PreAuthorize("hasRole('MAINTENANCE_CREW')")
    @Operation(summary = "Revise a rejected quotation")
    public ResponseEntity<QuotationResponse> reviseQuotation(@PathVariable Long id,
                                                              @Valid @RequestBody QuotationRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.reviseQuotation(id, request));
    }

    @PatchMapping("/{id}/approve-quote")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Approve a quotation")
    public ResponseEntity<MaintenanceFlagResponse> approveQuotation(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceFlagService.approveQuotation(id));
    }

    @PatchMapping("/{id}/reject-quote")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Reject a quotation")
    public ResponseEntity<MaintenanceFlagResponse> rejectQuotation(@PathVariable Long id,
                                                                    @Valid @RequestBody RejectQuotationRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.rejectQuotation(id, request));
    }

    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('MAINTENANCE_CREW')")
    @Operation(summary = "Update maintenance progress")
    public ResponseEntity<MaintenanceFlagResponse> updateProgress(@PathVariable Long id,
                                                                   @Valid @RequestBody ProgressUpdateRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.updateProgress(id, request));
    }

    @PatchMapping("/{id}/done")
    @PreAuthorize("hasRole('MAINTENANCE_CREW')")
    @Operation(summary = "Mark maintenance as done (pending approval)")
    public ResponseEntity<MaintenanceFlagResponse> markDone(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceFlagService.markDone(id));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Resolve a maintenance flag and rate the crew")
    public ResponseEntity<MaintenanceFlagResponse> resolve(@PathVariable Long id,
                                                           @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.resolve(id, request));
    }

    @PostMapping("/{id}/ratings")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Submit a rating for completed maintenance (alias via resolve)")
    public ResponseEntity<MaintenanceFlagResponse> rate(@PathVariable Long id,
                                                        @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(maintenanceFlagService.resolve(id, request));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN','MAINTENANCE_CREW')")
    @Operation(summary = "Send a message on a maintenance flag")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long id,
                                                        @Valid @RequestBody MessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceFlagService.sendMessage(id, request));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN','MAINTENANCE_CREW')")
    @Operation(summary = "Get messages on a maintenance flag")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceFlagService.getMessages(id));
    }
}
