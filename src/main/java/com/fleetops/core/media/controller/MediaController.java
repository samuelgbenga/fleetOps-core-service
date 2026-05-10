package com.fleetops.core.media.controller;

import com.fleetops.core.media.dto.MediaRequest;
import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    // ── User profile media ────────────────────────────────────────────────────

    @PatchMapping("/api/admin/users/{id}/media")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MediaResponse> setUserProfileMedia(
            @PathVariable Long id,
            @Valid @RequestBody MediaRequest request) {
        return ResponseEntity.ok(mediaService.setUserProfileMedia(id, request));
    }

    @DeleteMapping("/api/admin/users/{id}/media")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeUserProfileMedia(@PathVariable Long id) {
        mediaService.removeUserProfileMedia(id);
        return ResponseEntity.noContent().build();
    }

    // ── Vehicle media ─────────────────────────────────────────────────────────

    @PostMapping("/api/vehicles/{id}/media")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<List<MediaResponse>> addVehicleMedia(
            @PathVariable Long id,
            @Valid @RequestBody List<MediaRequest> requests) {
        return ResponseEntity.ok(mediaService.addVehicleMedia(id, requests));
    }

    @DeleteMapping("/api/vehicles/{id}/media/{mediaId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> removeVehicleMedia(
            @PathVariable Long id,
            @PathVariable Long mediaId) {
        mediaService.removeVehicleMedia(id, mediaId);
        return ResponseEntity.noContent().build();
    }
}
