package com.fleetops.core.assignment.controller;

import com.fleetops.core.assignment.dto.AssignmentResponse;
import com.fleetops.core.assignment.service.VehicleAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class VehicleAssignmentController {

    private final VehicleAssignmentService vehicleAssignmentService;

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AssignmentResponse>> getByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleAssignmentService.getAssignmentsByVehicle(vehicleId));
    }
}
