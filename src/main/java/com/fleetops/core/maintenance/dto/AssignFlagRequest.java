package com.fleetops.core.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignFlagRequest {
    @NotNull(message = "Maintenance team user ID is required")
    private Long maintenanceTeamUserId;
}
