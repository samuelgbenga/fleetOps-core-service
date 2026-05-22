package com.fleetops.core.module.user.controller;

import com.fleetops.core.module.user.dto.CreateUserRequest;
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
@RequestMapping("/api/platform/crew")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Platform - Crew")
public class PlatformCrewController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a maintenance crew member")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createCrewMember(request));
    }

    @GetMapping
    @Operation(summary = "List all maintenance crew")
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getAllCrew());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get crew member by ID")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getCrewById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a crew member")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteCrew(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/performance")
    @Operation(summary = "Get crew member performance (rating/jobs)")
    public ResponseEntity<UserResponse> getPerformance(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getCrewById(id));
    }
}
