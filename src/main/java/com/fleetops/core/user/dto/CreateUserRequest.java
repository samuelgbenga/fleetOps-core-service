package com.fleetops.core.user.dto;

import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @ValidPassword
    private String password;

    @NotNull(message = "Role is required")
    private UserRole role;
}
