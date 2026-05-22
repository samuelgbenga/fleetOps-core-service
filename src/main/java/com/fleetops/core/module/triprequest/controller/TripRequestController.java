package com.fleetops.core.module.triprequest.controller;

import com.fleetops.core.module.triprequest.dto.RejectTripRequest;
import com.fleetops.core.module.triprequest.dto.TripRequestCreate;
import com.fleetops.core.module.triprequest.dto.TripRequestResponse;
import com.fleetops.core.module.triprequest.service.TripRequestService;
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
@RequestMapping("/api/trip-requests")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Trip Requests")
public class TripRequestController {

    private final TripRequestService tripRequestService;

    @PostMapping
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Create a trip request")
    public ResponseEntity<TripRequestResponse> create(@Valid @RequestBody TripRequestCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripRequestService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "List all trip requests for company")
    public ResponseEntity<List<TripRequestResponse>> getAll() {
        return ResponseEntity.ok(tripRequestService.getAll());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "List all trip requests (alias)")
    public ResponseEntity<List<TripRequestResponse>> getAllAlias() {
        return ResponseEntity.ok(tripRequestService.getAll());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Get my trip requests")
    public ResponseEntity<List<TripRequestResponse>> getMy() {
        return ResponseEntity.ok(tripRequestService.getMy());
    }

    @GetMapping("/my/approved")
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Get my approved trip requests")
    public ResponseEntity<List<TripRequestResponse>> getMyApproved() {
        return ResponseEntity.ok(tripRequestService.getMyApproved());
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Approve a trip request")
    public ResponseEntity<TripRequestResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Reject a trip request")
    public ResponseEntity<TripRequestResponse> reject(@PathVariable Long id,
                                                       @Valid @RequestBody RejectTripRequest request) {
        return ResponseEntity.ok(tripRequestService.reject(id, request));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Mark trip as completed (awaits manager confirmation)")
    public ResponseEntity<TripRequestResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.complete(id));
    }

    @PatchMapping("/{id}/confirm-completion")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Confirm trip completion")
    public ResponseEntity<TripRequestResponse> confirmCompletion(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.confirmCompletion(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('FIELD_STAFF')")
    @Operation(summary = "Request cancellation of a pending or approved trip")
    public ResponseEntity<TripRequestResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.cancel(id));
    }

    @PatchMapping("/{id}/approve-cancellation")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','COMPANY_ADMIN')")
    @Operation(summary = "Approve a pending cancellation request")
    public ResponseEntity<TripRequestResponse> approveCancellation(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.approveCancellation(id));
    }
}
