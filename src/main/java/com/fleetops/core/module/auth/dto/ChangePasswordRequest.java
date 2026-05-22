package com.fleetops.core.module.auth.dto;

import com.fleetops.core.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank
    private String currentPassword;
    @NotBlank @ValidPassword
    private String newPassword;
}
