package com.fleetops.core.module.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotationRequest {
    @NotNull @DecimalMin("0.01")
    private BigDecimal estimatedCost;
    @NotBlank
    private String description;
    private String partsNeeded;
}
