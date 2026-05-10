package com.fleetops.core.media.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.media.dto.MediaRequest;
import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.media.entity.Media;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    // ── User profile media (One-to-One) ──────────────────────────────────────

    @Transactional
    public MediaResponse setUserProfileMedia(Long userId, MediaRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Media media = Media.builder()
                .publicId(request.getPublicId())
                .url(request.getUrl())
                .build();

        user.setProfileMedia(media);
        userRepository.save(user);
        return MediaResponse.from(user.getProfileMedia());
    }

    @Transactional
    public void removeUserProfileMedia(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getProfileMedia() == null) {
            throw new ConflictException("User " + userId + " has no profile media to remove");
        }

        user.setProfileMedia(null);
        userRepository.save(user);
    }

    // ── Vehicle media (One-to-Many) ───────────────────────────────────────────

    @Transactional
    public List<MediaResponse> addVehicleMedia(Long vehicleId, List<MediaRequest> requests) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        requests.stream()
                .map(r -> Media.builder().publicId(r.getPublicId()).url(r.getUrl()).build())
                .forEach(vehicle.getMediaFiles()::add);

        vehicleRepository.save(vehicle);
        return MediaResponse.fromList(vehicle.getMediaFiles());
    }

    @Transactional
    public void removeVehicleMedia(Long vehicleId, Long mediaId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        boolean removed = vehicle.getMediaFiles()
                .removeIf(m -> m.getId() != null && m.getId().equals(mediaId));

        if (!removed) {
            throw new ResourceNotFoundException(
                    "Media " + mediaId + " not found on vehicle " + vehicleId);
        }

        vehicleRepository.save(vehicle);
    }
}
