package com.fleetops.core.module.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String name;
    private String email;
    private String role;
    private String userType;
    private Long companyId;
}
