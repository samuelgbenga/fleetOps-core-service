package com.fleetops.core.module.user.controller;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.media.dto.UploadMediaRequest;
import com.fleetops.core.module.media.service.MediaService;
import com.fleetops.core.module.user.dto.UpdateProfileRequest;
import com.fleetops.core.module.user.dto.UserResponse;
import com.fleetops.core.module.user.service.UserService;
import com.fleetops.core.shared.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Profile")
public class ProfileController {

    private final UserService userService;
    private final MediaService mediaService;

    @GetMapping
    @Operation(summary = "Get my profile")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(userService.getMe());
    }

    @PatchMapping
    @Operation(summary = "Update my profile")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMe(request));
    }

    @PatchMapping("/media")
    @Operation(summary = "Save profile picture (url + publicId from Cloudinary)")
    public ResponseEntity<MediaResponse> uploadMedia(@Valid @RequestBody UploadMediaRequest request) {
        Long userId = TenantContext.getUserId();
        mediaService.deleteByOwner("USER", userId);
        request.setOwnerType("USER");
        request.setOwnerId(userId);
        MediaResponse response = mediaService.upload(request);
        userService.updateProfileImage(userId, response.getUrl(), response.getPublicId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/media")
    @Operation(summary = "Delete profile picture")
    public ResponseEntity<Void> deleteMedia() {
        Long userId = TenantContext.getUserId();
        mediaService.deleteByOwner("USER", userId);
        userService.clearProfileImage(userId);
        return ResponseEntity.noContent().build();
    }
}
