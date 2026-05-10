package com.fleetops.core.user.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.user.dto.CreateUserRequest;
import com.fleetops.core.user.dto.ResetPasswordRequest;
import com.fleetops.core.user.dto.UserResponse;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.media.dto.MediaRequest;
import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.media.entity.Media;
import com.fleetops.core.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationEventProducer notificationEventProducer;

    @InjectMocks private UserService userService;

    // ── createUser ───────────────────────────────────────────────────────────

    @Test
    void createUser_success_encodesPasswordAndSaves() {
        when(userRepository.existsByEmail("new@fleetops.com")).thenReturn(false);
        when(passwordEncoder.encode("plain123")).thenReturn("hashed123");
        User saved = User.builder().id(1L).name("New User").email("new@fleetops.com")
                .password("hashed123").role(UserRole.FIELD_STAFF).build();
        when(userRepository.save(any())).thenReturn(saved);

        CreateUserRequest req = createRequest("New User", "new@fleetops.com", "plain123", UserRole.FIELD_STAFF);

        UserResponse response = userService.createUser(req);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("new@fleetops.com");
        assertThat(response.getRole()).isEqualTo(UserRole.FIELD_STAFF);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed123");
    }

    @Test
    void createUser_success_sendsWelcomeNotificationWithCredentials() {
        when(userRepository.existsByEmail("new@fleetops.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed123");
        User saved = User.builder().id(1L).name("New User").email("new@fleetops.com")
                .password("hashed123").role(UserRole.FIELD_STAFF).build();
        when(userRepository.save(any())).thenReturn(saved);

        userService.createUser(createRequest("New User", "new@fleetops.com", "plain123", UserRole.FIELD_STAFF));

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        NotificationRequestEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo("ACCOUNT_CREATED");
        assertThat(event.getRecipientEmail()).isEqualTo("new@fleetops.com");
        // plain-text password and change-password advisory must be in the email body
        assertThat(event.getMessage()).contains("plain123");
        assertThat(event.getMessage()).contains("change your password");
    }

    @Test
    void createUser_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("dup@fleetops.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                createRequest("Dup", "dup@fleetops.com", "pass", UserRole.FIELD_STAFF)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("dup@fleetops.com");
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createUser_allRoles_createdSuccessfully() {
        for (UserRole role : UserRole.values()) {
            String email = role.name().toLowerCase() + "@fleetops.com";
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            User saved = User.builder().id(1L).name("User").email(email).password("hashed").role(role).build();
            when(userRepository.save(any())).thenReturn(saved);

            UserResponse response = userService.createUser(createRequest("User", email, "pass", role));

            assertThat(response.getRole()).isEqualTo(role);
        }
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "Alice", UserRole.FIELD_STAFF),
                user(2L, "Bob", UserRole.FLEET_MANAGER),
                user(3L, "Carol", UserRole.ADMIN)
        ));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(UserResponse::getName)
                .containsExactly("Alice", "Bob", "Carol");
    }

    @Test
    void getAllUsers_noUsers_returnsEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());
        assertThat(userService.getAllUsers()).isEmpty();
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_found_returnsResponse() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user(1L, "Alice", UserRole.FIELD_STAFF)));

        UserResponse response = userService.getUserById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alice");
    }

    @Test
    void getUserById_notFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_success_encodesAndOverwritesPassword() {
        User existing = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("HardSet@99")).thenReturn("hashed_new");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("HardSet@99");

        userService.resetPassword(1L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed_new");
    }

    @Test
    void resetPassword_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("AnyPass@1");

        assertThatThrownBy(() -> userService.resetPassword(99L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPassword_doesNotRequireCurrentPassword() {
        // Admin reset bypasses old-password verification entirely
        User existing = user(2L, "Bob", UserRole.FLEET_MANAGER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("ResetMe@1");

        userService.resetPassword(2L, req);

        verify(userRepository).save(any());
        // No call to passwordEncoder.matches — old password is never checked
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ── deactivateUser ───────────────────────────────────────────────────────

    @Test
    void deactivateUser_success_setsActiveToFalse() {
        User existing = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.deactivateUser(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void deactivateUser_alreadyInactive_throwsConflict() {
        User inactive = User.builder().id(1L).name("Alice")
                .email("alice@fleetops.com").role(UserRole.FIELD_STAFF)
                .password("hashed").active(false).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> userService.deactivateUser(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already deactivated");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_notFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(userRepository, never()).save(any());
    }

    // ── reactivateUser ───────────────────────────────────────────────────────

    @Test
    void reactivateUser_success_setsActiveToTrue() {
        User inactive = User.builder().id(1L).name("Alice")
                .email("alice@fleetops.com").role(UserRole.FIELD_STAFF)
                .password("hashed").active(false).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(inactive));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.reactivateUser(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void reactivateUser_alreadyActive_throwsConflict() {
        User active = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findById(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> userService.reactivateUser(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already active");
        verify(userRepository, never()).save(any());
    }

    @Test
    void reactivateUser_notFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.reactivateUser(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(userRepository, never()).save(any());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── getMyProfile ─────────────────────────────────────────────────────────

    @Test
    void getMyProfile_success_returnsOwnProfile() {
        mockSecurityContext("alice@fleetops.com");
        User alice = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findByEmail("alice@fleetops.com")).thenReturn(Optional.of(alice));

        UserResponse response = userService.getMyProfile();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alice");
        assertThat(response.getEmail()).isEqualTo("alice@fleetops.com");
    }

    @Test
    void getMyProfile_userNotFound_throwsResourceNotFound() {
        mockSecurityContext("ghost@fleetops.com");
        when(userRepository.findByEmail("ghost@fleetops.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateMyProfile ──────────────────────────────────────────────────────

    @Test
    void updateMyProfile_success_updatesNameOnly() {
        mockSecurityContext("alice@fleetops.com");
        User alice = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findByEmail("alice@fleetops.com")).thenReturn(Optional.of(alice));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Alice Updated");

        UserResponse response = userService.updateMyProfile(req);

        assertThat(response.getName()).isEqualTo("Alice Updated");
        assertThat(alice.getEmail()).isEqualTo("alice@fleetops.com");
    }

    @Test
    void updateMyProfile_userNotFound_throwsResourceNotFound() {
        mockSecurityContext("ghost@fleetops.com");
        when(userRepository.findByEmail("ghost@fleetops.com")).thenReturn(Optional.empty());

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Ghost");

        assertThatThrownBy(() -> userService.updateMyProfile(req))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // ── setMyProfileMedia ────────────────────────────────────────────────────

    @Test
    void setMyProfileMedia_success_setsMediaOnUser() {
        mockSecurityContext("alice@fleetops.com");
        User alice = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findByEmail("alice@fleetops.com")).thenReturn(Optional.of(alice));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MediaResponse response = userService.setMyProfileMedia(mediaRequest("pub-123", "https://cdn.example.com/img.jpg"));

        assertThat(response.getPublicId()).isEqualTo("pub-123");
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/img.jpg");
        assertThat(alice.getProfileMedia()).isNotNull();
    }

    @Test
    void setMyProfileMedia_replacesExisting() {
        mockSecurityContext("alice@fleetops.com");
        User alice = user(1L, "Alice", UserRole.FIELD_STAFF);
        alice.setProfileMedia(Media.builder().id(5L).publicId("old-id").url("https://old.url").build());
        when(userRepository.findByEmail("alice@fleetops.com")).thenReturn(Optional.of(alice));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.setMyProfileMedia(mediaRequest("new-id", "https://new.url"));

        assertThat(alice.getProfileMedia().getPublicId()).isEqualTo("new-id");
    }

    // ── removeMyProfileMedia ─────────────────────────────────────────────────

    @Test
    void removeMyProfileMedia_success_setsMediaToNull() {
        mockSecurityContext("alice@fleetops.com");
        User alice = user(1L, "Alice", UserRole.FIELD_STAFF);
        alice.setProfileMedia(Media.builder().id(5L).publicId("pub-123").url("https://cdn.example.com/img.jpg").build());
        when(userRepository.findByEmail("alice@fleetops.com")).thenReturn(Optional.of(alice));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.removeMyProfileMedia();

        assertThat(alice.getProfileMedia()).isNull();
        verify(userRepository).save(alice);
    }

    @Test
    void removeMyProfileMedia_noMedia_throwsConflict() {
        mockSecurityContext("alice@fleetops.com");
        User alice = user(1L, "Alice", UserRole.FIELD_STAFF);
        when(userRepository.findByEmail("alice@fleetops.com")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> userService.removeMyProfileMedia())
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no profile media");
        verify(userRepository, never()).save(any());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private MediaRequest mediaRequest(String publicId, String url) {
        MediaRequest req = new MediaRequest();
        req.setPublicId(publicId);
        req.setUrl(url);
        return req;
    }

    private User user(Long id, String name, UserRole role) {
        return User.builder().id(id).name(name)
                .email(name.toLowerCase() + "@fleetops.com")
                .password("hashed").role(role).build();
    }

    private CreateUserRequest createRequest(String name, String email, String password, UserRole role) {
        CreateUserRequest req = new CreateUserRequest();
        req.setName(name);
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }
}
