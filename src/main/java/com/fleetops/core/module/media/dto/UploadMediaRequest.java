package com.fleetops.core.module.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadMediaRequest {

    @NotBlank(message = "Public ID is required")
    private String publicId;

    @NotBlank(message = "URL is required")
    private String url;

    private String ownerType;

    private Long ownerId;
}
