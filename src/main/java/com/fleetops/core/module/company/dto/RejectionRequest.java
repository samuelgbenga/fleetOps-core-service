package com.fleetops.core.module.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectionRequest {
    @NotBlank
    private String reason;
}
