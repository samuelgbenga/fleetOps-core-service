package com.fleetops.core.module.maintenance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RatingRequest {
    @NotNull @Min(1) @Max(5)
    private Integer stars;
    private String comment;
    private java.math.BigDecimal actualCost;
}
