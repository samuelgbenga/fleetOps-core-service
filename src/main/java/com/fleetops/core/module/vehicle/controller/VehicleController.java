package com.fleetops.core.module.vehicle.controller;

import com.fleetops.core.module.vehicle.dto.MilestoneIntervalRequest;
import com.fleetops.core.module.vehicle.dto.VehicleImageRequest;
import com.fleetops.core.module.vehicle.dto.VehicleImageResponse;
import com.fleetops.core.module.vehicle.dto.VehicleRequest;
import com.fleetops.core.module.vehicle.dto.VehicleResponse;
import com.fleetops.core.module.vehicle.service.VehicleService;
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
@RequestMapping("/api/vehicles")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Register a vehicle")
    public ResponseEntity<VehicleResponse> register(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.registerVehicle(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "List all vehicles")
    public ResponseEntity<List<VehicleResponse>> getAll() {
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN','FIELD_STAFF')")
    @Operation(summary = "List available vehicles")
    public ResponseEntity<List<VehicleResponse>> getAvailable() {
        return ResponseEntity.ok(vehicleService.getAvailableVehicles());
    }

    @GetMapping("/my-trip-vehicles")
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Get vehicles for the current user's approved trips")
    public ResponseEntity<List<VehicleResponse>> getMyTripVehicles() {
        return ResponseEntity.ok(vehicleService.getMyTripVehicles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Get a vehicle by ID")
    public ResponseEntity<VehicleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    @GetMapping("/{id}/health")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Get vehicle health details")
    public ResponseEntity<VehicleResponse> getHealth(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    @PatchMapping("/{id}/milestone-interval")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Update vehicle milestone interval")
    public ResponseEntity<VehicleResponse> updateMilestoneInterval(@PathVariable Long id,
                                                                     @Valid @RequestBody MilestoneIntervalRequest request) {
        return ResponseEntity.ok(vehicleService.updateMilestoneInterval(id, request));
    }

    @PostMapping("/{id}/media")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Save vehicle image (url + imageId from Cloudinary)")
    public ResponseEntity<VehicleImageResponse> uploadImage(@PathVariable Long id,
                                                             @Valid @RequestBody VehicleImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.uploadVehicleImage(id, request));
    }

    @PostMapping("/{id}/media/bulk")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Save multiple vehicle images (url + imageId from Cloudinary)")
    public ResponseEntity<List<VehicleImageResponse>> bulkUploadImages(@PathVariable Long id,
                                                                        @RequestBody List<VehicleImageRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.uploadBulkVehicleImages(id, requests));
    }

    @DeleteMapping("/{id}/media/{imageId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Delete a vehicle image")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable String imageId) {
        vehicleService.deleteVehicleImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
