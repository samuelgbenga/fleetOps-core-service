package com.fleetops.core.media.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.media.dto.MediaRequest;
import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.media.entity.Media;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;

    @InjectMocks private MediaService mediaService;

    // ── setUserProfileMedia ──────────────────────────────────────────────────

    @Test
    void setUserProfileMedia_success_savesAndReturnsMedia() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getProfileMedia() != null) {
                setId(u.getProfileMedia(), 10L);
            }
            return u;
        });

        MediaResponse response = mediaService.setUserProfileMedia(1L, mediaRequest("pub123", "https://cdn.example.com/img.jpg"));

        assertThat(response.getPublicId()).isEqualTo("pub123");
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/img.jpg");
        assertThat(user.getProfileMedia()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void setUserProfileMedia_replacesExistingMedia() {
        User user = user(1L);
        user.setProfileMedia(media(5L, "old-id", "https://old.url"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mediaService.setUserProfileMedia(1L, mediaRequest("new-id", "https://new.url"));

        assertThat(user.getProfileMedia().getPublicId()).isEqualTo("new-id");
    }

    @Test
    void setUserProfileMedia_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.setUserProfileMedia(99L, mediaRequest("x", "y")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(userRepository, never()).save(any());
    }

    // ── removeUserProfileMedia ───────────────────────────────────────────────

    @Test
    void removeUserProfileMedia_success_setsMediaToNull() {
        User user = user(1L);
        user.setProfileMedia(media(5L, "pub123", "https://cdn.example.com/img.jpg"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mediaService.removeUserProfileMedia(1L);

        assertThat(user.getProfileMedia()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void removeUserProfileMedia_noMedia_throwsConflict() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> mediaService.removeUserProfileMedia(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no profile media");
        verify(userRepository, never()).save(any());
    }

    @Test
    void removeUserProfileMedia_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.removeUserProfileMedia(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── addVehicleMedia ──────────────────────────────────────────────────────

    @Test
    void addVehicleMedia_success_appendsAndReturnsAllMedia() {
        Vehicle vehicle = vehicle(10L);
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<MediaResponse> result = mediaService.addVehicleMedia(10L, List.of(
                mediaRequest("img-1", "https://cdn.example.com/1.jpg"),
                mediaRequest("img-2", "https://cdn.example.com/2.jpg")
        ));

        assertThat(vehicle.getMediaFiles()).hasSize(2);
        assertThat(result).hasSize(2);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void addVehicleMedia_appendsToExisting() {
        Vehicle vehicle = vehicle(10L);
        vehicle.getMediaFiles().add(media(1L, "existing", "https://cdn.example.com/existing.jpg"));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mediaService.addVehicleMedia(10L, List.of(mediaRequest("new-img", "https://cdn.example.com/new.jpg")));

        assertThat(vehicle.getMediaFiles()).hasSize(2);
    }

    @Test
    void addVehicleMedia_vehicleNotFound_throwsResourceNotFound() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.addVehicleMedia(99L, List.of(mediaRequest("x", "y"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(vehicleRepository, never()).save(any());
    }

    // ── removeVehicleMedia ───────────────────────────────────────────────────

    @Test
    void removeVehicleMedia_success_removesCorrectEntry() {
        Vehicle vehicle = vehicle(10L);
        vehicle.getMediaFiles().add(media(1L, "img-1", "https://cdn.example.com/1.jpg"));
        vehicle.getMediaFiles().add(media(2L, "img-2", "https://cdn.example.com/2.jpg"));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mediaService.removeVehicleMedia(10L, 1L);

        assertThat(vehicle.getMediaFiles()).hasSize(1);
        assertThat(vehicle.getMediaFiles().get(0).getPublicId()).isEqualTo("img-2");
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void removeVehicleMedia_mediaNotOnVehicle_throwsResourceNotFound() {
        Vehicle vehicle = vehicle(10L);
        vehicle.getMediaFiles().add(media(1L, "img-1", "https://cdn.example.com/1.jpg"));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> mediaService.removeVehicleMedia(10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void removeVehicleMedia_vehicleNotFound_throwsResourceNotFound() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.removeVehicleMedia(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User user(Long id) {
        return User.builder().id(id).name("Test User").email("test@fleetops.com")
                .role(UserRole.FIELD_STAFF).password("hashed").build();
    }

    private Vehicle vehicle(Long id) {
        return Vehicle.builder().id(id).make("Toyota").model("Hilux")
                .plateNumber("KJA-001AB").status(VehicleStatus.AVAILABLE)
                .currentMileage(0.0).milestoneInterval(5000.0)
                .mediaFiles(new ArrayList<>()).build();
    }

    private Media media(Long id, String publicId, String url) {
        return Media.builder().id(id).publicId(publicId).url(url).build();
    }

    private MediaRequest mediaRequest(String publicId, String url) {
        MediaRequest req = new MediaRequest();
        req.setPublicId(publicId);
        req.setUrl(url);
        return req;
    }

    private void setId(Media media, Long id) {
        try {
            var field = Media.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(media, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
