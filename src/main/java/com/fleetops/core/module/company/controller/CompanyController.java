package com.fleetops.core.module.company.controller;

import com.fleetops.core.module.company.dto.CompanyResponse;
import com.fleetops.core.module.company.dto.CompanyUpdateRequest;
import com.fleetops.core.module.company.dto.CompanyRegistrationRequest;
import com.fleetops.core.module.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Company")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping("/api/public/companies/register")
    @Operation(summary = "Register a new company")
    public ResponseEntity<CompanyResponse> register(@Valid @RequestBody CompanyRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.register(request));
    }

    @GetMapping("/api/company/profile")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get company profile")
    public ResponseEntity<CompanyResponse> getProfile() {
        return ResponseEntity.ok(companyService.getMyProfile());
    }

    @PatchMapping("/api/company/profile")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update company profile")
    public ResponseEntity<CompanyResponse> updateProfile(@Valid @RequestBody CompanyUpdateRequest request) {
        return ResponseEntity.ok(companyService.updateProfile(request));
    }
}
