package com.fleetops.core.module.breakdown.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DispatchCrewRequest {

    @NotNull(message = "Crew ID is required")
    private Long crewId;
}
