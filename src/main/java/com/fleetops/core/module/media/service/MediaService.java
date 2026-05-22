package com.fleetops.core.module.media.service;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.media.dto.UploadMediaRequest;

import java.util.List;

public interface MediaService {

    MediaResponse upload(UploadMediaRequest request);

    List<MediaResponse> getByOwner(String ownerType, Long ownerId);

    void deleteByPublicId(String publicId);

    void deleteByOwner(String ownerType, Long ownerId);
}
