package com.fleetops.core.module.media.service.impl;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.media.dto.UploadMediaRequest;
import com.fleetops.core.module.media.model.Media;
import com.fleetops.core.module.media.repository.MediaRepository;
import com.fleetops.core.module.media.service.MediaService;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;

    @Override
    @Transactional
    public MediaResponse upload(UploadMediaRequest request) {
        var media = Media.builder()
                .publicId(request.getPublicId())
                .url(request.getUrl())
                .ownerType(request.getOwnerType())
                .ownerId(request.getOwnerId())
                .build();
        return MediaResponse.from(mediaRepository.save(media));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaResponse> getByOwner(String ownerType, Long ownerId) {
        return mediaRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .stream()
                .map(MediaResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByPublicId(String publicId) {
        var media = mediaRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        mediaRepository.delete(media);
    }

    @Override
    @Transactional
    public void deleteByOwner(String ownerType, Long ownerId) {
        mediaRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }
}
