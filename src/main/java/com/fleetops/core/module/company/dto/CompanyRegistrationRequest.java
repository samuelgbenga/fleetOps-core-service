package com.fleetops.core.module.company.dto;

import com.fleetops.core.shared.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRegistrationRequest {
    @NotBlank
    private String name;
    @NotBlank @Email
    private String email;
    private String contactPhone;
    private String address;

    @NotBlank
    private String adminName;
    @NotBlank @Email
    private String adminEmail;
    @NotBlank @ValidPassword
    private String adminPassword;
}
