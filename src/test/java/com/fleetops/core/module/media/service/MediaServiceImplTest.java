package com.fleetops.core.module.media.service;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.media.dto.UploadMediaRequest;
import com.fleetops.core.module.media.model.Media;
import com.fleetops.core.module.media.repository.MediaRepository;
import com.fleetops.core.module.media.service.impl.MediaServiceImpl;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock private MediaRepository mediaRepository;
    @InjectMocks private MediaServiceImpl mediaService;

    // ═══════════════════════════════════════════════
    //  upload
    // ═══════════════════════════════════════════════

    @Test
    void upload_validRequest_returnsSavedMedia() {
        var request = uploadRequest("pub-1", "https://cdn.io/img.jpg", "VEHICLE", 5L);
        Media saved = media(1L, "pub-1", "https://cdn.io/img.jpg", "VEHICLE", 5L);
        when(mediaRepository.save(any())).thenReturn(saved);

        MediaResponse response = mediaService.upload(request);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void upload_setsPublicIdCorrectly() {
        var request = uploadRequest("pub-abc", "https://url.com/x.png", "USER", 1L);
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0, Media.class));

        mediaService.upload(request);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        assertThat(captor.getValue().getPublicId()).isEqualTo("pub-abc");
    }

    @Test
    void upload_setsUrlCorrectly() {
        var request = uploadRequest("pub-1", "https://cdn.io/photo.jpg", "USER", 1L);
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0, Media.class));

        mediaService.upload(request);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo("https://cdn.io/photo.jpg");
    }

    @Test
    void upload_setsOwnerTypeCorrectly() {
        var request = uploadRequest("pub-1", "https://url.com/img.png", "VEHICLE", 3L);
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0, Media.class));

        mediaService.upload(request);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerType()).isEqualTo("VEHICLE");
    }

    @Test
    void upload_setsOwnerIdCorrectly() {
        var request = uploadRequest("pub-1", "https://url.com/img.png", "VEHICLE", 99L);
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0, Media.class));

        mediaService.upload(request);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(99L);
    }

    @Test
    void upload_callsSaveOnce() {
        var request = uploadRequest("pub-1", "url", "USER", 1L);
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0, Media.class));

        mediaService.upload(request);

        verify(mediaRepository, times(1)).save(any());
    }

    @Test
    void upload_responseContainsPublicId() {
        var request = uploadRequest("pub-xyz", "url", "USER", 1L);
        Media saved = media(1L, "pub-xyz", "url", "USER", 1L);
        when(mediaRepository.save(any())).thenReturn(saved);

        MediaResponse response = mediaService.upload(request);

        assertThat(response.getPublicId()).isEqualTo("pub-xyz");
    }

    @Test
    void upload_responseContainsUrl() {
        var request = uploadRequest("pub-1", "https://cdn.io/img.jpg", "VEHICLE", 5L);
        Media saved = media(1L, "pub-1", "https://cdn.io/img.jpg", "VEHICLE", 5L);
        when(mediaRepository.save(any())).thenReturn(saved);

        MediaResponse response = mediaService.upload(request);

        assertThat(response.getUrl()).isEqualTo("https://cdn.io/img.jpg");
    }

    @Test
    void upload_noDeletionSideEffect() {
        when(mediaRepository.save(any())).thenAnswer(i -> i.getArgument(0, Media.class));

        mediaService.upload(uploadRequest("p1", "u1", "USER", 1L));

        verify(mediaRepository, never()).delete(any());
        verify(mediaRepository, never()).deleteByOwnerTypeAndOwnerId(any(), any());
    }

    // ═══════════════════════════════════════════════
    //  getByOwner
    // ═══════════════════════════════════════════════

    @Test
    void getByOwner_returnsAllMatchingMedia() {
        when(mediaRepository.findByOwnerTypeAndOwnerId("VEHICLE", 5L))
                .thenReturn(List.of(media(1L, "p1", "u1", "VEHICLE", 5L),
                        media(2L, "p2", "u2", "VEHICLE", 5L)));

        List<MediaResponse> result = mediaService.getByOwner("VEHICLE", 5L);

        assertThat(result).hasSize(2);
    }

    @Test
    void getByOwner_returnsEmptyListWhenNoMedia() {
        when(mediaRepository.findByOwnerTypeAndOwnerId("VEHICLE", 99L)).thenReturn(List.of());

        List<MediaResponse> result = mediaService.getByOwner("VEHICLE", 99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getByOwner_mapsPublicIdCorrectly() {
        when(mediaRepository.findByOwnerTypeAndOwnerId("USER", 1L))
                .thenReturn(List.of(media(1L, "pub-abc", "url", "USER", 1L)));

        List<MediaResponse> result = mediaService.getByOwner("USER", 1L);

        assertThat(result.get(0).getPublicId()).isEqualTo("pub-abc");
    }

    @Test
    void getByOwner_callsRepositoryWithCorrectArgs() {
        when(mediaRepository.findByOwnerTypeAndOwnerId("VEHICLE", 10L)).thenReturn(List.of());

        mediaService.getByOwner("VEHICLE", 10L);

        verify(mediaRepository).findByOwnerTypeAndOwnerId("VEHICLE", 10L);
    }

    @Test
    void getByOwner_neverSaves() {
        when(mediaRepository.findByOwnerTypeAndOwnerId(any(), any())).thenReturn(List.of());

        mediaService.getByOwner("USER", 1L);

        verify(mediaRepository, never()).save(any());
    }

    // ═══════════════════════════════════════════════
    //  deleteByPublicId
    // ═══════════════════════════════════════════════

    @Test
    void deleteByPublicId_existingMedia_deletesIt() {
        Media m = media(1L, "pub-1", "url", "VEHICLE", 5L);
        when(mediaRepository.findByPublicId("pub-1")).thenReturn(Optional.of(m));

        mediaService.deleteByPublicId("pub-1");

        verify(mediaRepository).delete(m);
    }

    @Test
    void deleteByPublicId_notFound_throwsResourceNotFoundException() {
        when(mediaRepository.findByPublicId("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.deleteByPublicId("no-such"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteByPublicId_notFound_neverCallsDelete() {
        when(mediaRepository.findByPublicId("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.deleteByPublicId("no-such"));
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void deleteByPublicId_noSaveSideEffect() {
        Media m = media(1L, "pub-1", "url", "USER", 1L);
        when(mediaRepository.findByPublicId("pub-1")).thenReturn(Optional.of(m));

        mediaService.deleteByPublicId("pub-1");

        verify(mediaRepository, never()).save(any());
    }

    // ═══════════════════════════════════════════════
    //  deleteByOwner
    // ═══════════════════════════════════════════════

    @Test
    void deleteByOwner_callsRepositoryDeleteByOwner() {
        mediaService.deleteByOwner("VEHICLE", 5L);

        verify(mediaRepository).deleteByOwnerTypeAndOwnerId("VEHICLE", 5L);
    }

    @Test
    void deleteByOwner_neverSaves() {
        mediaService.deleteByOwner("USER", 1L);

        verify(mediaRepository, never()).save(any());
    }

    @Test
    void deleteByOwner_doesNotThrowWhenNoMediaExists() {
        doNothing().when(mediaRepository).deleteByOwnerTypeAndOwnerId(any(), any());

        assertThatCode(() -> mediaService.deleteByOwner("VEHICLE", 999L))
                .doesNotThrowAnyException();
    }

    // ═══════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════

    private UploadMediaRequest uploadRequest(String publicId, String url, String ownerType, Long ownerId) {
        var r = new UploadMediaRequest();
        r.setPublicId(publicId);
        r.setUrl(url);
        r.setOwnerType(ownerType);
        r.setOwnerId(ownerId);
        return r;
    }

    private Media media(Long id, String publicId, String url, String ownerType, Long ownerId) {
        return Media.builder().id(id).publicId(publicId).url(url).ownerType(ownerType).ownerId(ownerId).build();
    }
}
