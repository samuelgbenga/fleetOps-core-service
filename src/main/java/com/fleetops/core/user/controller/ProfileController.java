package com.fleetops.core.user.controller;

import com.fleetops.core.media.dto.MediaRequest;
import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.user.dto.UpdateProfileRequest;
import com.fleetops.core.user.dto.UserResponse;
import com.fleetops.core.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @PatchMapping
    public ResponseEntity<UserResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }

    @PatchMapping("/media")
    public ResponseEntity<MediaResponse> setMyProfileMedia(
            @Valid @RequestBody MediaRequest request) {
        return ResponseEntity.ok(userService.setMyProfileMedia(request));
    }

    @DeleteMapping("/media")
    public ResponseEntity<Void> removeMyProfileMedia() {
        userService.removeMyProfileMedia();
        return ResponseEntity.noContent().build();
    }
}
