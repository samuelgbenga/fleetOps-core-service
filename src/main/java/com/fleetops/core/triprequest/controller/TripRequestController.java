package com.fleetops.core.triprequest.controller;

import com.fleetops.core.triprequest.dto.TripRequestCreate;
import com.fleetops.core.triprequest.dto.TripRequestResponse;
import com.fleetops.core.triprequest.service.TripRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trip-requests")
@RequiredArgsConstructor
public class TripRequestController {

    private final TripRequestService tripRequestService;

    @PostMapping
    @PreAuthorize("hasRole('FIELD_STAFF')")
    public ResponseEntity<TripRequestResponse> create(@Valid @RequestBody TripRequestCreate dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripRequestService.createRequest(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public ResponseEntity<List<TripRequestResponse>> getPending() {
        return ResponseEntity.ok(tripRequestService.getPendingRequests());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('FIELD_STAFF')")
    public ResponseEntity<List<TripRequestResponse>> getMine() {
        return ResponseEntity.ok(tripRequestService.getMyRequests());
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public ResponseEntity<TripRequestResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.approveRequest(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public ResponseEntity<TripRequestResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(tripRequestService.rejectRequest(id));
    }
}
