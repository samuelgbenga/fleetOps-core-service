package com.fleetops.core.vehicle.controller;

import com.fleetops.core.vehicle.dto.VehicleRequest;
import com.fleetops.core.vehicle.dto.VehicleResponse;
import com.fleetops.core.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public ResponseEntity<VehicleResponse> register(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.registerVehicle(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<VehicleResponse>> getAll() {
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('FIELD_STAFF', 'FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<VehicleResponse>> getAvailable() {
        return ResponseEntity.ok(vehicleService.getAvailableVehicles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<VehicleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }
}
