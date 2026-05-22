package com.fleetops.core.module.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProgressUpdateRequest {
    @NotBlank
    private String progressNotes;
}
