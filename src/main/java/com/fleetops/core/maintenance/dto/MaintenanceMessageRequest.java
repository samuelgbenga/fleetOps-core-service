package com.fleetops.core.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaintenanceMessageRequest {

    @NotBlank(message = "Message cannot be blank")
    private String message;
}
