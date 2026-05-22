package com.fleetops.core.module.user.controller;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.media.dto.UploadMediaRequest;
import com.fleetops.core.module.media.service.MediaService;
import com.fleetops.core.module.user.dto.CreateUserRequest;
import com.fleetops.core.module.user.dto.ResetPasswordRequest;
import com.fleetops.core.module.user.dto.UserResponse;
import com.fleetops.core.module.user.service.UserService;
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
@PreAuthorize("hasRole('COMPANY_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Company Users")
public class CompanyUserController {

    private final UserService userService;
    private final MediaService mediaService;

    @PostMapping("/api/company/users")
    @Operation(summary = "Create a company user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createCompanyUser(request));
    }

    @GetMapping("/api/company/users")
    @Operation(summary = "List all company users")
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getCompanyUsers());
    }

    @GetMapping("/api/company/users/{id}")
    @Operation(summary = "Get a company user by ID")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getCompanyUserById(id));
    }

    @PatchMapping("/api/company/users/{id}/deactivate")
    @Operation(summary = "Deactivate a user")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/company/users/{id}/reactivate")
    @Operation(summary = "Reactivate a user")
    public ResponseEntity<Void> reactivate(@PathVariable Long id) {
        userService.reactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/company/users/{id}/reset-password")
    @Operation(summary = "Reset a user's password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                               @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/users/{id}/media")
    @Operation(summary = "Save profile picture for a user (url + publicId from Cloudinary)")
    public ResponseEntity<MediaResponse> uploadMedia(@PathVariable Long id,
                                                      @Valid @RequestBody UploadMediaRequest request) {
        mediaService.deleteByOwner("USER", id);
        request.setOwnerType("USER");
        request.setOwnerId(id);
        MediaResponse response = mediaService.upload(request);
        userService.updateProfileImage(id, response.getUrl(), response.getPublicId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/admin/users/{id}/media")
    @Operation(summary = "Delete profile picture for a user")
    public ResponseEntity<Void> deleteMedia(@PathVariable Long id) {
        mediaService.deleteByOwner("USER", id);
        userService.clearProfileImage(id);
        return ResponseEntity.noContent().build();
    }
}
