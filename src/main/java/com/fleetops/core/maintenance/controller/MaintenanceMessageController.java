package com.fleetops.core.maintenance.controller;

import com.fleetops.core.maintenance.dto.MaintenanceMessageRequest;
import com.fleetops.core.maintenance.dto.MaintenanceMessageResponse;
import com.fleetops.core.maintenance.service.MaintenanceMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance-flags/{flagId}/messages")
@RequiredArgsConstructor
public class MaintenanceMessageController {

    private final MaintenanceMessageService messageService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MAINTENANCE_TEAM', 'FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceMessageResponse> send(
            @PathVariable Long flagId,
            @Valid @RequestBody MaintenanceMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(flagId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MAINTENANCE_TEAM', 'FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<MaintenanceMessageResponse>> getMessages(@PathVariable Long flagId) {
        return ResponseEntity.ok(messageService.getMessages(flagId));
    }
}
