package com.fleetops.core.media.dto;

import com.fleetops.core.media.entity.Media;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MediaResponse {

    private Long id;
    private String publicId;
    private String url;

    public static MediaResponse from(Media media) {
        if (media == null) return null;
        return MediaResponse.builder()
                .id(media.getId())
                .publicId(media.getPublicId())
                .url(media.getUrl())
                .build();
    }

    public static List<MediaResponse> fromList(List<Media> mediaList) {
        if (mediaList == null) return List.of();
        return mediaList.stream().map(MediaResponse::from).toList();
    }
}
