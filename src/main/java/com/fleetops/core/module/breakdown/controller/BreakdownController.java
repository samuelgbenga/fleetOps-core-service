package com.fleetops.core.module.breakdown.controller;

import com.fleetops.core.module.breakdown.dto.BreakdownReportRequest;
import com.fleetops.core.module.breakdown.dto.BreakdownResponse;
import com.fleetops.core.module.breakdown.dto.DispatchReplacementRequest;
import com.fleetops.core.module.breakdown.service.BreakdownService;
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
@RequestMapping("/api/breakdowns")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Breakdowns")
public class BreakdownController {

    private final BreakdownService breakdownService;

    @PostMapping
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Report a breakdown")
    public ResponseEntity<BreakdownResponse> report(@Valid @RequestBody BreakdownReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(breakdownService.report(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "List all breakdowns for company")
    public ResponseEntity<List<BreakdownResponse>> getAll() {
        return ResponseEntity.ok(breakdownService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN','FIELD_STAFF')")
    @Operation(summary = "Get a breakdown by ID")
    public ResponseEntity<BreakdownResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(breakdownService.getById(id));
    }

    @PatchMapping("/{id}/dispatch-replacement")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Dispatch a replacement vehicle")
    public ResponseEntity<BreakdownResponse> dispatchReplacement(@PathVariable Long id,
                                                                   @Valid @RequestBody DispatchReplacementRequest request) {
        return ResponseEntity.ok(breakdownService.dispatchReplacement(id, request));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Resolve a breakdown")
    public ResponseEntity<BreakdownResponse> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(breakdownService.resolve(id));
    }
}
