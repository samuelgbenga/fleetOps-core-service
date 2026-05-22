package com.fleetops.core.module.user.dto;

import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.shared.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank
    private String name;
    @NotBlank @Email
    private String email;
    @NotBlank @ValidPassword
    private String password;
    @NotNull
    private Role role;
}
