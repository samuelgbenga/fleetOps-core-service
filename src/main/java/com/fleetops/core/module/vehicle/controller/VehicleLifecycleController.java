package com.fleetops.core.module.vehicle.controller;

import com.fleetops.core.module.vehicle.service.VehicleLifecyclePdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Vehicles")
public class VehicleLifecycleController {

    private final VehicleLifecyclePdfService pdfService;

    @GetMapping("/{vehicleId}/lifecycle.pdf")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Download vehicle lifecycle PDF report")
    public ResponseEntity<byte[]> downloadLifecyclePdf(@PathVariable Long vehicleId) {
        byte[] pdf = pdfService.generate(vehicleId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"vehicle-lifecycle-" + vehicleId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
