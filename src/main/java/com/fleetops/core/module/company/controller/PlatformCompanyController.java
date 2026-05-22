package com.fleetops.core.module.company.controller;

import com.fleetops.core.module.company.dto.CompanyResponse;
import com.fleetops.core.module.company.dto.RejectionRequest;
import com.fleetops.core.module.company.service.CompanyService;
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
@RequestMapping("/api/platform/companies")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Platform - Companies")
public class PlatformCompanyController {

    private final CompanyService companyService;

    @GetMapping
    @Operation(summary = "List all companies")
    public ResponseEntity<List<CompanyResponse>> getAll() {
        return ResponseEntity.ok(companyService.getAllCompanies());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get company by ID")
    public ResponseEntity<CompanyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.getCompanyById(id));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve a company")
    public ResponseEntity<CompanyResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.approveCompany(id));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject a company")
    public ResponseEntity<CompanyResponse> reject(@PathVariable Long id,
                                                   @Valid @RequestBody RejectionRequest request) {
        return ResponseEntity.ok(companyService.rejectCompany(id, request));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend a company")
    public ResponseEntity<CompanyResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.suspendCompany(id));
    }

    @PatchMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate a company")
    public ResponseEntity<CompanyResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.reactivateCompany(id));
    }
}
