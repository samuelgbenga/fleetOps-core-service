package com.fleetops.core.auth.service;

import com.fleetops.core.auth.dto.AuthResponse;
import com.fleetops.core.auth.dto.ChangePasswordRequest;
import com.fleetops.core.auth.dto.LoginRequest;
import com.fleetops.core.auth.util.JwtUtil;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokenAndRole() {
        User user = User.builder()
                .id(1L).name("Admin").email("admin@fleetops.com")
                .password("hashed").role(UserRole.ADMIN).build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail("admin@fleetops.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("mock.jwt.token");

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@fleetops.com");
        request.setPassword("Admin@1234");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getEmail()).isEqualTo("admin@fleetops.com");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void login_differentRoles_correctRoleInResponse() {
        User manager = User.builder()
                .id(2L).name("Manager").email("manager@fleetops.com")
                .password("hashed").role(UserRole.FLEET_MANAGER).build();

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(jwtUtil.generateToken(manager)).thenReturn("manager.token");

        LoginRequest request = new LoginRequest();
        request.setEmail("manager@fleetops.com");
        request.setPassword("password");

        AuthResponse response = authService.login(request);

        assertThat(response.getRole()).isEqualTo("FLEET_MANAGER");
    }

    @Test
    void login_badCredentials_propagatesException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@fleetops.com");
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_deactivatedAccount_propagatesDisabledException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("User is disabled"));

        LoginRequest request = new LoginRequest();
        request.setEmail("inactive@fleetops.com");
        request.setPassword("anypass");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DisabledException.class);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_userNotInDbAfterAuth_throwsUsernameNotFoundException() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("ghost@fleetops.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@fleetops.com");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // ── changePassword ───────────────────────────────────────────────────────

    @Test
    void changePassword_success_encodesAndSavesNewPassword() {
        mockSecurityContext("staff@fleetops.com");
        User user = user(1L, "staff@fleetops.com", "hashed_old");

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@1", "hashed_old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("hashed_new");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("OldPass@1");
        req.setNewPassword("NewPass@2");

        authService.changePassword(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed_new");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadCredentials() {
        mockSecurityContext("staff@fleetops.com");
        User user = user(1L, "staff@fleetops.com", "hashed_old");

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "hashed_old")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("WrongPass");
        req.setNewPassword("NewPass@2");

        assertThatThrownBy(() -> authService.changePassword(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("incorrect");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotFound_throwsResourceNotFound() {
        mockSecurityContext("ghost@fleetops.com");
        when(userRepository.findByEmail("ghost@fleetops.com")).thenReturn(Optional.empty());

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("anything");
        req.setNewPassword("NewPass@2");

        assertThatThrownBy(() -> authService.changePassword(req))
                .isInstanceOf(ResourceNotFoundException.class);
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

    private User user(Long id, String email, String hashedPassword) {
        return User.builder().id(id).name("Test User").email(email)
                .password(hashedPassword).role(UserRole.FIELD_STAFF).build();
    }
}
