package com.fleetops.core.module.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectQuotationRequest {
    @NotBlank
    private String reason;
}
