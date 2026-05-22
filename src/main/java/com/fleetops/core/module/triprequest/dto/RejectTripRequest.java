package com.fleetops.core.module.triprequest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectTripRequest {
    @NotBlank
    private String reason;
}
