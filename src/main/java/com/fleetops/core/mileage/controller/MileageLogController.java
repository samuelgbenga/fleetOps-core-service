package com.fleetops.core.mileage.controller;

import com.fleetops.core.mileage.dto.MileageLogRequest;
import com.fleetops.core.mileage.dto.MileageLogResponse;
import com.fleetops.core.mileage.service.MileageLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mileage-logs")
@RequiredArgsConstructor
public class MileageLogController {

    private final MileageLogService mileageLogService;

    @PostMapping
    @PreAuthorize("hasRole('FIELD_STAFF')")
    public ResponseEntity<MileageLogResponse> submit(@Valid @RequestBody MileageLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mileageLogService.submitLog(request));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<MileageLogResponse>> getByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(mileageLogService.getLogsByVehicle(vehicleId));
    }
}
