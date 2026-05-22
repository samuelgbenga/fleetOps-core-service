package com.fleetops.core.module.user.service;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.user.dto.*;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.user.service.impl.UserServiceImpl;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.shared.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock NotificationEventProducer notificationEventProducer;

    @InjectMocks UserServiceImpl userService;

    private static final Long COMPANY_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@test.com";

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, USER_ID, "COMPANY_ADMIN", "COMPANY");
        var auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private User buildUser(Long id, String email, Role role, Company company) {
        return User.builder()
                .id(id).name("Test User").email(email)
                .password("encoded").role(role)
                .userType(company != null ? UserType.COMPANY : UserType.PLATFORM)
                .company(company).active(true).available(role == Role.FIELD_STAFF)
                .build();
    }

    private Company buildCompany() {
        return Company.builder().id(COMPANY_ID).name("Fleet Co").build();
    }

    // ─── getMe ────────────────────────────────────────────────────────────────

    @Test
    void getMe_returnsUserResponse() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        var result = userService.getMe();
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void getMe_returnsCorrectId() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getId()).isEqualTo(USER_ID);
    }

    @Test
    void getMe_returnsCorrectRole() {
        User user = buildUser(USER_ID, EMAIL, Role.COMPANY_ADMIN, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getRole()).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    void getMe_returnsCorrectCompanyId() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void getMe_throwsWhenUserNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getMe()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMe_lookupBySecurityContextEmail() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        userService.getMe();
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void getMe_neverCallsSave() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        userService.getMe();
        verify(userRepository, never()).save(any());
    }

    @Test
    void getMe_platformUserNullCompanyId() {
        User user = buildUser(USER_ID, EMAIL, Role.PLATFORM_ADMIN, null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getCompanyId()).isNull();
    }

    @Test
    void getMe_activeStatusReflected() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().isActive()).isTrue();
    }

    @Test
    void getMe_userTypeReflected() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getUserType()).isEqualTo("COMPANY");
    }

    @Test
    void getMe_noNotificationSideEffect() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        userService.getMe();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getMe_noPasswordEncoderInteraction() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        userService.getMe();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getMe_calledExactlyOnce() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        userService.getMe();
        verify(userRepository, times(1)).findByEmail(EMAIL);
    }

    @Test
    void getMe_differentEmailsReturnCorrectUser() {
        String otherEmail = "other@test.com";
        var auth = new UsernamePasswordAuthenticationToken(otherEmail, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        User user = buildUser(2L, otherEmail, Role.COMPANY_ADMIN, buildCompany());
        when(userRepository.findByEmail(otherEmail)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getEmail()).isEqualTo(otherEmail);
    }

    @Test
    void getMe_returnsNameCorrectly() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThat(userService.getMe().getName()).isEqualTo("Test User");
    }

    // ─── updateMe ─────────────────────────────────────────────────────────────

    @Test
    void updateMe_updatesName() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        var req = new UpdateProfileRequest();
        req.setName("New Name");
        var result = userService.updateMe(req);
        assertThat(result).isNotNull();
    }

    @Test
    void updateMe_setsNameOnUser() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        var req = new UpdateProfileRequest();
        req.setName("Updated Name");
        userService.updateMe(req);
        assertThat(user.getName()).isEqualTo("Updated Name");
    }

    @Test
    void updateMe_nullNameNotOverwritten() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        var req = new UpdateProfileRequest();
        req.setName(null);
        userService.updateMe(req);
        assertThat(user.getName()).isEqualTo("Test User");
    }

    @Test
    void updateMe_callsSave() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.updateMe(new UpdateProfileRequest());
        verify(userRepository).save(user);
    }

    @Test
    void updateMe_userNotFoundThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateMe(new UpdateProfileRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMe_userNotFoundNeverSaves() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateMe(new UpdateProfileRequest()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMe_returnsResponseWithCorrectId() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        assertThat(userService.updateMe(new UpdateProfileRequest()).getId()).isEqualTo(USER_ID);
    }

    @Test
    void updateMe_noNotificationSideEffect() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.updateMe(new UpdateProfileRequest());
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void updateMe_noPasswordEncoderInteraction() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.updateMe(new UpdateProfileRequest());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMe_saveCalledOnce() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.updateMe(new UpdateProfileRequest());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void updateMe_lookupBySecurityContextEmail() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.updateMe(new UpdateProfileRequest());
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void updateMe_noCompanyRepoInteraction() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.updateMe(new UpdateProfileRequest());
        verifyNoInteractions(companyRepository);
    }

    @Test
    void updateMe_returnsResponseWithCorrectEmail() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        assertThat(userService.updateMe(new UpdateProfileRequest()).getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void updateMe_emailCannotBeChanged() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        var req = new UpdateProfileRequest();
        req.setName("New Name");
        var result = userService.updateMe(req);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void updateMe_responseContainsRole() {
        User user = buildUser(USER_ID, EMAIL, Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        assertThat(userService.updateMe(new UpdateProfileRequest()).getRole()).isEqualTo("FLEET_MANAGER");
    }

    // ─── createCompanyUser ────────────────────────────────────────────────────

    @Test
    void createCompanyUser_returnsResponse() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("New User"); req.setEmail("new@test.com");
        req.setPassword("pass"); req.setRole(Role.FLEET_MANAGER);

        assertThat(userService.createCompanyUser(req)).isNotNull();
    }

    @Test
    void createCompanyUser_duplicateEmailThrows() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);
        var req = new CreateUserRequest();
        req.setEmail("dup@test.com"); req.setName("X"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        assertThatThrownBy(() -> userService.createCompanyUser(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void createCompanyUser_duplicateEmailNeverSaves() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);
        var req = new CreateUserRequest();
        req.setEmail("dup@test.com"); req.setName("X"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        assertThatThrownBy(() -> userService.createCompanyUser(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createCompanyUser_passwordIsEncoded() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(passwordEncoder.encode("plain")).thenReturn("encoded");
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("plain"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);
        verify(passwordEncoder).encode("plain");
    }

    @Test
    void createCompanyUser_plainTextNeverStored() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(passwordEncoder.encode("plain")).thenReturn("hashed");
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("plain"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo("plain");
    }

    @Test
    void createCompanyUser_sendsNotification() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void createCompanyUser_usesCompanyFromTenantContext() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);
        verify(companyRepository).findById(COMPANY_ID);
    }

    @Test
    void createCompanyUser_companyNotFoundThrows() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());
        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        assertThatThrownBy(() -> userService.createCompanyUser(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCompanyUser_fieldStaffIsAvailable() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("fs@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(3L, "fs@test.com", Role.FIELD_STAFF, company);
        newUser.setAvailable(true);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("FS"); req.setEmail("fs@test.com"); req.setPassword("p"); req.setRole(Role.FIELD_STAFF);
        userService.createCompanyUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailable()).isTrue();
    }

    @Test
    void createCompanyUser_nonFieldStaffNotAvailable() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("fm@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(4L, "fm@test.com", Role.FLEET_MANAGER, company);
        newUser.setAvailable(false);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("FM"); req.setEmail("fm@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailable()).isFalse();
    }

    @Test
    void createCompanyUser_userTypeIsCompany() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.COMPANY);
    }

    @Test
    void createCompanyUser_linkedToCompany() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany()).isEqualTo(company);
    }

    @Test
    void createCompanyUser_notificationContainsEmail() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);

        ArgumentCaptor<NotificationRequestEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getRecipientEmail()).isEqualTo("new@test.com");
    }

    @Test
    void createCompanyUser_notificationTypeUserCreated() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);

        ArgumentCaptor<NotificationRequestEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("USER_CREATED");
    }

    @Test
    void createCompanyUser_saveCalledOnce() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(2L, "new@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("new@test.com"); req.setPassword("p"); req.setRole(Role.FLEET_MANAGER);
        userService.createCompanyUser(req);
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void createCompanyUser_roleSetCorrectly() {
        Company company = buildCompany();
        when(userRepository.existsByEmail("ca@test.com")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        var newUser = buildUser(5L, "ca@test.com", Role.COMPANY_ADMIN, company);
        when(userRepository.save(any())).thenReturn(newUser);

        var req = new CreateUserRequest();
        req.setName("X"); req.setEmail("ca@test.com"); req.setPassword("p"); req.setRole(Role.COMPANY_ADMIN);
        userService.createCompanyUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.COMPANY_ADMIN);
    }

    // ─── getCompanyUsers ──────────────────────────────────────────────────────

    @Test
    void getCompanyUsers_returnsAllUsers() {
        Company company = buildCompany();
        List<User> users = List.of(
                buildUser(1L, "u1@test.com", Role.FLEET_MANAGER, company),
                buildUser(2L, "u2@test.com", Role.FIELD_STAFF, company)
        );
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(users);
        assertThat(userService.getCompanyUsers()).hasSize(2);
    }

    @Test
    void getCompanyUsers_emptyList() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        assertThat(userService.getCompanyUsers()).isEmpty();
    }

    @Test
    void getCompanyUsers_usesCompanyIdFromTenant() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verify(userRepository).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getCompanyUsers_mapsEmails() {
        Company company = buildCompany();
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildUser(1L, "u1@test.com", Role.FLEET_MANAGER, company)));
        assertThat(userService.getCompanyUsers().get(0).getEmail()).isEqualTo("u1@test.com");
    }

    @Test
    void getCompanyUsers_neverSaves() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verify(userRepository, never()).save(any());
    }

    @Test
    void getCompanyUsers_noNotificationInteraction() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getCompanyUsers_noCompanyRepoInteraction() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getCompanyUsers_findByCompanyIdCalledOnce() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verify(userRepository, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getCompanyUsers_mapsRoles() {
        Company company = buildCompany();
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildUser(1L, "u1@test.com", Role.COMPANY_ADMIN, company)));
        assertThat(userService.getCompanyUsers().get(0).getRole()).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    void getCompanyUsers_singleItemList() {
        Company company = buildCompany();
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildUser(1L, "u1@test.com", Role.FLEET_MANAGER, company)));
        assertThat(userService.getCompanyUsers()).hasSize(1);
    }

    @Test
    void getCompanyUsers_mapsIds() {
        Company company = buildCompany();
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildUser(99L, "u1@test.com", Role.FLEET_MANAGER, company)));
        assertThat(userService.getCompanyUsers().get(0).getId()).isEqualTo(99L);
    }

    @Test
    void getCompanyUsers_largeListReturned() {
        Company company = buildCompany();
        List<User> users = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> buildUser((long) i, "u" + i + "@test.com", Role.FIELD_STAFF, company))
                .toList();
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(users);
        assertThat(userService.getCompanyUsers()).hasSize(50);
    }

    @Test
    void getCompanyUsers_noPasswordEncoderInteraction() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getCompanyUsers_doesNotCallFindByRole() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verify(userRepository, never()).findByRole(any());
    }

    @Test
    void getCompanyUsers_doesNotCallFindAll() {
        when(userRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        userService.getCompanyUsers();
        verify(userRepository, never()).findAll();
    }

    // ─── getCompanyUserById ───────────────────────────────────────────────────

    @Test
    void getCompanyUserById_returnsUser() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThat(userService.getCompanyUserById(5L)).isNotNull();
    }

    @Test
    void getCompanyUserById_wrongCompanyThrows() {
        Company otherCompany = Company.builder().id(99L).name("Other").build();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, otherCompany);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.getCompanyUserById(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompanyUserById_notFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getCompanyUserById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompanyUserById_noCompanyThrows() {
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, null);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.getCompanyUserById(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompanyUserById_returnsCorrectEmail() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThat(userService.getCompanyUserById(5L).getEmail()).isEqualTo("u5@test.com");
    }

    @Test
    void getCompanyUserById_neverSaves() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        userService.getCompanyUserById(5L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getCompanyUserById_lookupByCorrectId() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        userService.getCompanyUserById(5L);
        verify(userRepository).findById(5L);
    }

    @Test
    void getCompanyUserById_noNotificationInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        userService.getCompanyUserById(5L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getCompanyUserById_returnsCorrectRole() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.COMPANY_ADMIN, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThat(userService.getCompanyUserById(5L).getRole()).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    void getCompanyUserById_tenantContextCompanyIdUsed() {
        TenantContext.set(20L, USER_ID, "COMPANY_ADMIN", "COMPANY");
        Company otherCompany = Company.builder().id(10L).name("Other").build();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, otherCompany);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.getCompanyUserById(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCompanyUserById_returnsCorrectId() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThat(userService.getCompanyUserById(5L).getId()).isEqualTo(5L);
    }

    @Test
    void getCompanyUserById_noPasswordEncoderInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        userService.getCompanyUserById(5L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getCompanyUserById_noCompanyRepoInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        userService.getCompanyUserById(5L);
        verifyNoInteractions(companyRepository);
    }

    // ─── deactivateUser ───────────────────────────────────────────────────────

    @Test
    void deactivateUser_setsActiveFalse() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void deactivateUser_savesCalled() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_notFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deactivateUser(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateUser_notFoundNeverSaves() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deactivateUser(99L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_wrongCompanyThrows() {
        Company otherCompany = Company.builder().id(99L).name("Other").build();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, otherCompany);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.deactivateUser(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateUser_noNotificationInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void deactivateUser_saveCalledOnce() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void deactivateUser_noPasswordEncoderInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void deactivateUser_lookupByCorrectId() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        verify(userRepository).findById(5L);
    }

    @Test
    void deactivateUser_previouslyActiveNowDeactivated() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        user.setActive(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void deactivateUser_noCompanyRepoInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.deactivateUser(5L);
        verifyNoInteractions(companyRepository);
    }

    // ─── reactivateUser ───────────────────────────────────────────────────────

    @Test
    void reactivateUser_setsActiveTrue() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        user.setActive(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void reactivateUser_savesCalled() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        verify(userRepository).save(user);
    }

    @Test
    void reactivateUser_notFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.reactivateUser(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivateUser_notFoundNeverSaves() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.reactivateUser(99L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void reactivateUser_wrongCompanyThrows() {
        Company otherCompany = Company.builder().id(99L).name("Other").build();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, otherCompany);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.reactivateUser(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivateUser_noNotificationInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void reactivateUser_saveCalledOnce() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void reactivateUser_noPasswordEncoderInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void reactivateUser_lookupByCorrectId() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        verify(userRepository).findById(5L);
    }

    @Test
    void reactivateUser_noCompanyRepoInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        userService.reactivateUser(5L);
        verifyNoInteractions(companyRepository);
    }

    // ─── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_encodesNewPassword() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNew");

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verify(passwordEncoder).encode("newPass");
    }

    @Test
    void resetPassword_setsEncodedPassword() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNew");

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        assertThat(user.getPassword()).isEqualTo("encodedNew");
    }

    @Test
    void resetPassword_plainTextNeverStored() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNew");

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        assertThat(user.getPassword()).isNotEqualTo("newPass");
    }

    @Test
    void resetPassword_notFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.resetPassword(99L, new ResetPasswordRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_notFoundNeverSaves() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.resetPassword(99L, new ResetPasswordRequest()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_savesCalled() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_wrongCompanyThrows() {
        Company otherCompany = Company.builder().id(99L).name("Other").build();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, otherCompany);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.resetPassword(5L, new ResetPasswordRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_noNotificationInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void resetPassword_saveCalledOnce() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void resetPassword_encoderCalledOnce() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("encodedNew");

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verify(passwordEncoder, times(1)).encode(any());
    }

    @Test
    void resetPassword_noCompanyRepoInteraction() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void resetPassword_lookupByCorrectId() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);
        verify(userRepository).findById(5L);
    }

    @Test
    void resetPassword_saveOrderAfterEncode() {
        Company company = buildCompany();
        User user = buildUser(5L, "u5@test.com", Role.FLEET_MANAGER, company);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNew");

        var req = new ResetPasswordRequest();
        req.setNewPassword("newPass");
        userService.resetPassword(5L, req);

        var inOrder = inOrder(passwordEncoder, userRepository);
        inOrder.verify(passwordEncoder).encode("newPass");
        inOrder.verify(userRepository).save(user);
    }

    // ─── createCrewMember ─────────────────────────────────────────────────────

    @Test
    void createCrewMember_returnsResponse() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        crew.setUserType(UserType.PLATFORM);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        assertThat(userService.createCrewMember(req)).isNotNull();
    }

    @Test
    void createCrewMember_duplicateEmailThrows() {
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(true);
        var req = new CreateUserRequest();
        req.setEmail("crew@test.com"); req.setName("X"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        assertThatThrownBy(() -> userService.createCrewMember(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void createCrewMember_duplicateEmailNeverSaves() {
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(true);
        var req = new CreateUserRequest();
        req.setEmail("crew@test.com"); req.setName("X"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        assertThatThrownBy(() -> userService.createCrewMember(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createCrewMember_passwordEncoded() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(passwordEncoder.encode("plain")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("plain"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);
        verify(passwordEncoder).encode("plain");
    }

    @Test
    void createCrewMember_roleIsMaintenanceCrew() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.MAINTENANCE_CREW);
    }

    @Test
    void createCrewMember_userTypeIsPlatform() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.PLATFORM);
    }

    @Test
    void createCrewMember_availableIsTrue() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        crew.setAvailable(true);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailable()).isTrue();
    }

    @Test
    void createCrewMember_sendsNotification() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void createCrewMember_notLinkedToCompany() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany()).isNull();
    }

    @Test
    void createCrewMember_noCompanyRepoInteraction() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void createCrewMember_notificationTypeUserCreated() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);

        ArgumentCaptor<NotificationRequestEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("USER_CREATED");
    }

    @Test
    void createCrewMember_saveCalledOnce() {
        var crew = buildUser(10L, "crew@test.com", Role.MAINTENANCE_CREW, null);
        when(userRepository.existsByEmail("crew@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(crew);

        var req = new CreateUserRequest();
        req.setName("Crew"); req.setEmail("crew@test.com"); req.setPassword("p"); req.setRole(Role.MAINTENANCE_CREW);
        userService.createCrewMember(req);
        verify(userRepository, times(1)).save(any());
    }

    // ─── getAllCrew ───────────────────────────────────────────────────────────

    @Test
    void getAllCrew_returnsCrewList() {
        List<User> crews = List.of(
                buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany()),
                buildUser(2L, "c2@test.com", Role.MAINTENANCE_CREW, null)
        );
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(crews);
        assertThat(userService.getAllCrew()).hasSize(2);
    }

    @Test
    void getAllCrew_emptyList() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        assertThat(userService.getAllCrew()).isEmpty();
    }

    @Test
    void getAllCrew_usesMaintenanceCrewRole() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verify(userRepository).findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID);
    }

    @Test
    void getAllCrew_neverSaves() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verify(userRepository, never()).save(any());
    }

    @Test
    void getAllCrew_noNotificationInteraction() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAllCrew_noCompanyRepoInteraction() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getAllCrew_findByRoleCalledOnce() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verify(userRepository, times(1)).findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID);
    }

    @Test
    void getAllCrew_mapsEmails() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of(crew));
        assertThat(userService.getAllCrew().get(0).getEmail()).isEqualTo("c1@test.com");
    }

    @Test
    void getAllCrew_mapsRoles() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of(crew));
        assertThat(userService.getAllCrew().get(0).getRole()).isEqualTo("MAINTENANCE_CREW");
    }

    @Test
    void getAllCrew_noPasswordEncoderInteraction() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getAllCrew_doesNotCallFindByCompanyId() {
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of());
        userService.getAllCrew();
        verify(userRepository, never()).findByCompanyId(any());
    }

    @Test
    void getAllCrew_singleCrew() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(List.of(crew));
        assertThat(userService.getAllCrew()).hasSize(1);
    }

    @Test
    void getAllCrew_largeList() {
        List<User> crews = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> buildUser((long) i, "c" + i + "@test.com", Role.MAINTENANCE_CREW, null))
                .toList();
        when(userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, COMPANY_ID)).thenReturn(crews);
        assertThat(userService.getAllCrew()).hasSize(30);
    }

    // ─── getCrewById ──────────────────────────────────────────────────────────

    @Test
    void getCrewById_returnsCrew() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        assertThat(userService.getCrewById(1L)).isNotNull();
    }

    @Test
    void getCrewById_notMaintenanceCrewThrows() {
        var user = buildUser(1L, "u1@test.com", Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.getCrewById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCrewById_notFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getCrewById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCrewById_returnsCorrectEmail() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        assertThat(userService.getCrewById(1L).getEmail()).isEqualTo("c1@test.com");
    }

    @Test
    void getCrewById_neverSaves() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.getCrewById(1L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getCrewById_lookupByCorrectId() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.getCrewById(1L);
        verify(userRepository).findById(1L);
    }

    @Test
    void getCrewById_noNotificationInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.getCrewById(1L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getCrewById_noCompanyRepoInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.getCrewById(1L);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getCrewById_returnsCorrectRole() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        assertThat(userService.getCrewById(1L).getRole()).isEqualTo("MAINTENANCE_CREW");
    }

    @Test
    void getCrewById_noPasswordEncoderInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.getCrewById(1L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getCrewById_findByIdCalledOnce() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.getCrewById(1L);
        verify(userRepository, times(1)).findById(1L);
    }

    // ─── deleteCrew ───────────────────────────────────────────────────────────

    @Test
    void deleteCrew_deletesCalled() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verify(userRepository).delete(crew);
    }

    @Test
    void deleteCrew_notMaintenanceCrewThrows() {
        var user = buildUser(1L, "u1@test.com", Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.deleteCrew(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCrew_notFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deleteCrew(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCrew_notFoundNeverDeletes() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deleteCrew(99L));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteCrew_notMaintenanceCrewNeverDeletes() {
        var user = buildUser(1L, "u1@test.com", Role.FLEET_MANAGER, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> userService.deleteCrew(1L));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteCrew_deleteCalledOnce() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verify(userRepository, times(1)).delete(any(User.class));
    }

    @Test
    void deleteCrew_noNotificationInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void deleteCrew_noCompanyRepoInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void deleteCrew_noPasswordEncoderInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void deleteCrew_lookupByCorrectId() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verify(userRepository).findById(1L);
    }

    @Test
    void deleteCrew_deletesCorrectEntity() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void deleteCrew_noSaveInteraction() {
        var crew = buildUser(1L, "c1@test.com", Role.MAINTENANCE_CREW, buildCompany());
        when(userRepository.findById(1L)).thenReturn(Optional.of(crew));
        userService.deleteCrew(1L);
        verify(userRepository, never()).save(any());
    }
}
