package com.fleetops.core.activity.controller;

import com.fleetops.core.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.activity.service.VehicleActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/activity-logs")
@RequiredArgsConstructor
public class VehicleActivityLogController {

    private final VehicleActivityLogService activityLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VehicleActivityLogResponse>> search(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(activityLogService.search(plateNumber, date));
    }
}
