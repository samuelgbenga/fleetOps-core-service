package com.fleetops.core.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaRequest {

    @NotBlank(message = "Cloudinary public ID is required")
    private String publicId;

    @NotBlank(message = "URL is required")
    private String url;
}
