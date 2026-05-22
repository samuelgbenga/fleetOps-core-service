package com.fleetops.core.module.auth.service;

import com.fleetops.core.module.auth.dto.AuthResponse;
import com.fleetops.core.module.auth.dto.ChangePasswordRequest;
import com.fleetops.core.module.auth.dto.LoginRequest;
import com.fleetops.core.module.auth.service.impl.AuthServiceImpl;
import com.fleetops.core.module.auth.util.JwtUtil;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthServiceImpl authService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ═══════════════════════════════════════════════
    //  login
    // ═══════════════════════════════════════════════

    @Test
    void login_validCredentials_returnsToken() {
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("jwt.token.here");

        AuthResponse response = authService.login(loginRequest("user@test.com", "pass"));

        assertThat(response.getToken()).isEqualTo("jwt.token.here");
    }

    @Test
    void login_validCredentials_returnsCorrectUserId() {
        User user = companyUser(42L, "user@test.com", Role.FLEET_MANAGER);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("user@test.com", "pass"));

        assertThat(response.getUserId()).isEqualTo(42L);
    }

    @Test
    void login_validCredentials_returnsCorrectEmail() {
        User user = companyUser(1L, "fleet@corp.com", Role.FLEET_MANAGER);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("fleet@corp.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("fleet@corp.com", "pass"));

        assertThat(response.getEmail()).isEqualTo("fleet@corp.com");
    }

    @Test
    void login_validCredentials_returnsCorrectRole() {
        User user = companyUser(1L, "user@test.com", Role.COMPANY_ADMIN);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("user@test.com", "pass"));

        assertThat(response.getRole()).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    void login_companyUser_returnsCompanyId() {
        Company company = Company.builder().id(7L).build();
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setCompany(company);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("user@test.com", "pass"));

        assertThat(response.getCompanyId()).isEqualTo(7L);
    }

    @Test
    void login_platformUser_returnsNullCompanyId() {
        User user = platformUser(1L, "admin@platform.com", Role.PLATFORM_ADMIN);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("admin@platform.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("admin@platform.com", "pass"));

        assertThat(response.getCompanyId()).isNull();
    }

    @Test
    void login_validCredentials_returnsCorrectUserType() {
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("user@test.com", "pass"));

        assertThat(response.getUserType()).isEqualTo("COMPANY");
    }

    @Test
    void login_validCredentials_returnsCorrectName() {
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setName("John Doe");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("user@test.com", "pass"));

        assertThat(response.getName()).isEqualTo("John Doe");
    }

    @Test
    void login_badCredentials_throwsBadCredentialsException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest("user@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_badCredentials_neverCallsUserRepository() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest("user@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_disabledAccount_throwsDisabledException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("Account disabled"));

        assertThatThrownBy(() -> authService.login(loginRequest("inactive@test.com", "pass")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void login_userNotFoundAfterAuth_throwsResourceNotFoundException() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("ghost@test.com", "pass")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void login_authenticationManagerCalledWithCorrectCredentials() {
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        authService.login(loginRequest("user@test.com", "secretpass"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("user@test.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("secretpass");
    }

    @Test
    void login_jwtGeneratedForCorrectUser() {
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        authService.login(loginRequest("user@test.com", "pass"));

        verify(jwtUtil).generateToken(user);
    }

    @Test
    void login_differentRoles_eachRoleReturned() {
        for (Role role : new Role[]{Role.FIELD_STAFF, Role.MAINTENANCE_CREW, Role.PLATFORM_ADMIN}) {
            User user = companyUser(1L, "u@test.com", role);
            when(authenticationManager.authenticate(any())).thenReturn(null);
            when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
            when(jwtUtil.generateToken(user)).thenReturn("token");

            AuthResponse response = authService.login(loginRequest("u@test.com", "pass"));
            assertThat(response.getRole()).isEqualTo(role.name());
        }
    }

    @Test
    void login_fieldStaffUser_userTypeIsCompany() {
        User user = companyUser(1L, "staff@test.com", Role.FIELD_STAFF);
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(loginRequest("staff@test.com", "pass"));

        assertThat(response.getUserType()).isEqualTo("COMPANY");
    }

    // ═══════════════════════════════════════════════
    //  changePassword
    // ═══════════════════════════════════════════════

    @Test
    void changePassword_correctCurrentPassword_savesEncodedNewPassword() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("hashed_old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@1", "hashed_old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("hashed_new");

        authService.changePassword(changePasswordRequest("OldPass@1", "NewPass@2"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed_new");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadCredentials() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("hashed_old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed_old")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(changePasswordRequest("wrong", "NewPass@2")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    void changePassword_wrongPassword_neverSaves() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("hashed_old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed_old")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(changePasswordRequest("wrong", "NewPass@2")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotFound_throwsResourceNotFoundException() {
        mockSecurityContext("ghost@test.com");
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(changePasswordRequest("any", "NewPass@2")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changePassword_userNotFound_neverCallsPasswordEncoder() {
        mockSecurityContext("ghost@test.com");
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(changePasswordRequest("any", "NewPass@2")));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changePassword_newPasswordIsEncoded() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("hashed_old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@1", "hashed_old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("hashed_new");

        authService.changePassword(changePasswordRequest("OldPass@1", "NewPass@2"));

        verify(passwordEncoder).encode("NewPass@2");
    }

    @Test
    void changePassword_oldPasswordCheckedBeforeEncoding() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("hashed_old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@1", "hashed_old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("hashed_new");

        authService.changePassword(changePasswordRequest("OldPass@1", "NewPass@2"));

        var order = inOrder(passwordEncoder);
        order.verify(passwordEncoder).matches("OldPass@1", "hashed_old");
        order.verify(passwordEncoder).encode("NewPass@2");
    }

    @Test
    void changePassword_userLookedUpFromSecurityContext() {
        mockSecurityContext("ctx@test.com");
        User user = companyUser(1L, "ctx@test.com", Role.FLEET_MANAGER);
        user.setPassword("hash");
        when(userRepository.findByEmail("ctx@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("new_hash");

        authService.changePassword(changePasswordRequest("current", "New@pass1"));

        verify(userRepository).findByEmail("ctx@test.com");
    }

    @Test
    void changePassword_saveCalledOnce() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old_plain", "old")).thenReturn(true);
        when(passwordEncoder.encode("New@pass1")).thenReturn("new_encoded");

        authService.changePassword(changePasswordRequest("old_plain", "New@pass1"));

        verify(userRepository, times(1)).save(any());
    }

    @Test
    void changePassword_plaintextPasswordNeverStored() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@1", "old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("safe_encoded");

        authService.changePassword(changePasswordRequest("OldPass@1", "NewPass@2"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).doesNotContain("NewPass@2");
    }

    @Test
    void changePassword_platformAdminCanChangePassword() {
        mockSecurityContext("admin@platform.com");
        User admin = platformUser(1L, "admin@platform.com", Role.PLATFORM_ADMIN);
        admin.setPassword("old_hash");
        when(userRepository.findByEmail("admin@platform.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("OldPass@1", "old_hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("new_hash");

        assertThatCode(() -> authService.changePassword(changePasswordRequest("OldPass@1", "NewPass@2")))
                .doesNotThrowAnyException();
    }

    @Test
    void changePassword_samePasswordStillAllowed() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("old_hash");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass@1234", "old_hash")).thenReturn(true);
        when(passwordEncoder.encode("Pass@1234")).thenReturn("re_encoded");

        assertThatCode(() -> authService.changePassword(changePasswordRequest("Pass@1234", "Pass@1234")))
                .doesNotThrowAnyException();
    }

    @Test
    void changePassword_passwordMatcherUsesStoredHash() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("$2a$10$storedHash");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@1", "$2a$10$storedHash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@2")).thenReturn("new");

        authService.changePassword(changePasswordRequest("OldPass@1", "NewPass@2"));

        verify(passwordEncoder).matches("OldPass@1", "$2a$10$storedHash");
    }

    @Test
    void changePassword_noJwtInteraction() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@1", "old")).thenReturn(true);
        when(passwordEncoder.encode("New@1")).thenReturn("encoded");

        authService.changePassword(changePasswordRequest("Old@1", "New@1"));

        verifyNoInteractions(jwtUtil);
    }

    @Test
    void changePassword_noAuthManagerInteraction() {
        mockSecurityContext("user@test.com");
        User user = companyUser(1L, "user@test.com", Role.FLEET_MANAGER);
        user.setPassword("old");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@1", "old")).thenReturn(true);
        when(passwordEncoder.encode("New@1")).thenReturn("encoded");

        authService.changePassword(changePasswordRequest("Old@1", "New@1"));

        verifyNoInteractions(authenticationManager);
    }

    // ═══════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════

    private LoginRequest loginRequest(String email, String password) {
        var req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private ChangePasswordRequest changePasswordRequest(String current, String next) {
        var req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(next);
        return req;
    }

    private User companyUser(Long id, String email, Role role) {
        Company company = Company.builder().id(10L).name("Test Corp").build();
        return User.builder()
                .id(id).name("Test User").email(email)
                .password("hashed").role(role).userType(UserType.COMPANY)
                .company(company).active(true).build();
    }

    private User platformUser(Long id, String email, Role role) {
        return User.builder()
                .id(id).name("Platform User").email(email)
                .password("hashed").role(role).userType(UserType.PLATFORM)
                .active(true).build();
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
