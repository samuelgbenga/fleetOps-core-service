package com.fleetops.core.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProgressUpdateRequest {
    @NotBlank(message = "Progress notes are required")
    private String progressNotes;
}
