package com.fleetops.core.user.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.user.dto.CreateUserRequest;
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

    // ── helpers ─────────────────────────────────────────────────────────────

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
