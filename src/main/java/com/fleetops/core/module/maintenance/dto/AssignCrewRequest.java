package com.fleetops.core.module.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignCrewRequest {
    @NotNull
    private Long crewId;
}
