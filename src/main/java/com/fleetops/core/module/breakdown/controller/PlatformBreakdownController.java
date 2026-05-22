package com.fleetops.core.module.breakdown.controller;

import com.fleetops.core.module.breakdown.dto.BreakdownResponse;
import com.fleetops.core.module.breakdown.dto.DispatchCrewRequest;
import com.fleetops.core.module.breakdown.service.BreakdownService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/breakdowns")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Platform - Breakdowns")
public class PlatformBreakdownController {

    private final BreakdownService breakdownService;

    @GetMapping
    @Operation(summary = "List all breakdowns (platform)")
    public ResponseEntity<List<BreakdownResponse>> getAll() {
        return ResponseEntity.ok(breakdownService.getAllPlatform());
    }

    @PatchMapping("/{id}/dispatch-crew")
    @Operation(summary = "Dispatch maintenance crew to a breakdown")
    public ResponseEntity<BreakdownResponse> dispatchCrew(@PathVariable Long id,
                                                           @Valid @RequestBody DispatchCrewRequest request) {
        return ResponseEntity.ok(breakdownService.dispatchCrew(id, request));
    }
}
