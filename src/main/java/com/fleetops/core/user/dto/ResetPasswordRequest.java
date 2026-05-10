package com.fleetops.core.user.dto;

import com.fleetops.core.validation.ValidPassword;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @ValidPassword
    private String newPassword;
}
