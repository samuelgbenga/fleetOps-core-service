package com.fleetops.core.module.media.dto;

import com.fleetops.core.module.media.model.Media;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaResponse {

    private Long id;
    private String publicId;
    private String url;
    private String ownerType;
    private Long ownerId;

    public static MediaResponse from(Media media) {
        return MediaResponse.builder()
                .id(media.getId())
                .publicId(media.getPublicId())
                .url(media.getUrl())
                .ownerType(media.getOwnerType())
                .ownerId(media.getOwnerId())
                .build();
    }
}
