package com.fleetops.core.module.user.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;
    private String profileImageUrl;
    private String profileImageId;
}
