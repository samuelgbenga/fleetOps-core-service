package com.fleetops.core.module.media.repository;

import com.fleetops.core.module.media.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByPublicId(String publicId);
    List<Media> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
    List<Media> findByOwnerTypeAndOwnerIdIn(String ownerType, List<Long> ownerIds);
    void deleteByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}
