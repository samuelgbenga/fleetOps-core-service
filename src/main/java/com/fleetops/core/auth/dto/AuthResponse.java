package com.fleetops.core.auth.dto;

import com.fleetops.core.media.dto.MediaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String name;
    private String email;
    private String role;
    private MediaResponse profileMedia;
}
