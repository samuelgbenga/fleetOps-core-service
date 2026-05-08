package com.fleetops.core.auth.service;

import com.fleetops.core.auth.dto.AuthResponse;
import com.fleetops.core.auth.dto.LoginRequest;
import com.fleetops.core.auth.util.JwtUtil;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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

    @InjectMocks private AuthService authService;

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
    void login_userNotInDbAfterAuth_throwsUsernameNotFoundException() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("ghost@fleetops.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@fleetops.com");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
