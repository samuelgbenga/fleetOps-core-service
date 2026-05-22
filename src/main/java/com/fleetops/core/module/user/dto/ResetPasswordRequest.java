package com.fleetops.core.module.user.dto;

import com.fleetops.core.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank @ValidPassword
    private String newPassword;
}
