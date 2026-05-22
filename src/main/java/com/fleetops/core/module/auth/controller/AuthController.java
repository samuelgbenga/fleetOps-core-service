package com.fleetops.core.module.auth.controller;

import com.fleetops.core.module.auth.dto.AuthResponse;
import com.fleetops.core.module.auth.dto.ChangePasswordRequest;
import com.fleetops.core.module.auth.dto.LoginRequest;
import com.fleetops.core.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/public/auth/login")
    @Operation(summary = "Login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PatchMapping("/api/auth/change-password")
    @Operation(summary = "Change password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
