package com.fleetops.core.module.mileage.controller;

import com.fleetops.core.module.mileage.dto.MileageLogRequest;
import com.fleetops.core.module.mileage.dto.MileageLogResponse;
import com.fleetops.core.module.mileage.service.MileageLogService;
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
@RequestMapping("/api/mileage-logs")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Mileage Logs")
public class MileageLogController {

    private final MileageLogService mileageLogService;

    @PostMapping
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Submit a mileage log")
    public ResponseEntity<MileageLogResponse> submit(@Valid @RequestBody MileageLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mileageLogService.submit(request));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Get mileage logs for a vehicle")
    public ResponseEntity<List<MileageLogResponse>> getByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(mileageLogService.getByVehicle(vehicleId));
    }
}
