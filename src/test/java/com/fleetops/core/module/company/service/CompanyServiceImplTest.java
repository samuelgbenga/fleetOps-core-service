package com.fleetops.core.module.company.service;

import com.fleetops.core.module.company.dto.*;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.model.CompanyStatus;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.company.service.impl.CompanyServiceImpl;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationEventProducer notificationEventProducer;

    @InjectMocks private CompanyServiceImpl companyService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ═══════════════════════════════════════════════
    //  register
    // ═══════════════════════════════════════════════

    @Test
    void register_newCompany_returnsResponse() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        when(companyRepository.existsByEmail("corp@test.com")).thenReturn(false);
        when(companyRepository.existsByName("Test Corp")).thenReturn(false);
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        Company saved = company(1L, "Test Corp", CompanyStatus.PENDING);
        when(companyRepository.save(any())).thenReturn(saved);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanyResponse response = companyService.register(request);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void register_newCompany_statusIsPending() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        Company saved = company(1L, "Test Corp", CompanyStatus.PENDING);
        when(companyRepository.save(any())).thenReturn(saved);

        CompanyResponse response = companyService.register(request);

        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void register_newCompany_adminUserCreatedWithCorrectRole() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        Company saved = company(1L, "Test Corp", CompanyStatus.PENDING);
        when(companyRepository.save(any())).thenReturn(saved);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.COMPANY_ADMIN);
    }

    @Test
    void register_newCompany_adminUserInactive() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        Company saved = company(1L, "Test Corp", CompanyStatus.PENDING);
        when(companyRepository.save(any())).thenReturn(saved);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isActive()).isFalse();
    }

    @Test
    void register_newCompany_passwordEncoded() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        request.setAdminPassword("Admin@1234");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        Company saved = company(1L, "Test Corp", CompanyStatus.PENDING);
        when(companyRepository.save(any())).thenReturn(saved);
        when(passwordEncoder.encode("Admin@1234")).thenReturn("hashed_pw");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed_pw");
    }

    @Test
    void register_duplicateCompanyEmail_throwsConflictException() {
        var request = registrationRequest("Test Corp", "dup@test.com", "admin@test.com");
        when(companyRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> companyService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
    }

    @Test
    void register_duplicateCompanyEmail_neverSavesCompany() {
        var request = registrationRequest("Test Corp", "dup@test.com", "admin@test.com");
        when(companyRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> companyService.register(request));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void register_duplicateCompanyName_throwsConflictException() {
        var request = registrationRequest("Taken Name", "corp@test.com", "admin@test.com");
        when(companyRepository.existsByEmail("corp@test.com")).thenReturn(false);
        when(companyRepository.existsByName("Taken Name")).thenReturn(true);

        assertThatThrownBy(() -> companyService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("name");
    }

    @Test
    void register_duplicateAdminEmail_throwsConflictException() {
        var request = registrationRequest("Test Corp", "corp@test.com", "taken@test.com");
        when(companyRepository.existsByEmail("corp@test.com")).thenReturn(false);
        when(companyRepository.existsByName("Test Corp")).thenReturn(false);
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> companyService.register(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void register_newCompany_notificationSent() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        when(companyRepository.save(any())).thenReturn(company(1L, "Test Corp", CompanyStatus.PENDING));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        verify(notificationEventProducer, times(1)).publish(any());
    }

    @Test
    void register_adminUserHasCompanyUserType() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        when(companyRepository.save(any())).thenReturn(company(1L, "Test Corp", CompanyStatus.PENDING));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.COMPANY);
    }

    @Test
    void register_adminUserLinkedToCompany() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        Company saved = company(1L, "Test Corp", CompanyStatus.PENDING);
        when(companyRepository.save(any())).thenReturn(saved);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany()).isNotNull();
        assertThat(captor.getValue().getCompany().getId()).isEqualTo(1L);
    }

    @Test
    void register_companyNameSetCorrectly() {
        var request = registrationRequest("Acme Ltd", "acme@test.com", "admin@acme.com");
        setupRegisterMocks("acme@test.com", "Acme Ltd", "admin@acme.com");
        when(companyRepository.save(any())).thenReturn(company(1L, "Acme Ltd", CompanyStatus.PENDING));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanyResponse response = companyService.register(request);

        assertThat(response.getName()).isEqualTo("Acme Ltd");
    }

    @Test
    void register_plainPasswordNeverStoredInUser() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        request.setAdminPassword("PlainPass@1");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        when(companyRepository.save(any())).thenReturn(company(1L, "Test Corp", CompanyStatus.PENDING));
        when(passwordEncoder.encode("PlainPass@1")).thenReturn("safe_hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).doesNotContain("PlainPass@1");
    }

    @Test
    void register_companyAndUserBothSaved() {
        var request = registrationRequest("Test Corp", "corp@test.com", "admin@test.com");
        setupRegisterMocks("corp@test.com", "Test Corp", "admin@test.com");
        when(companyRepository.save(any())).thenReturn(company(1L, "Test Corp", CompanyStatus.PENDING));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.register(request);

        verify(companyRepository, times(1)).save(any());
        verify(userRepository, times(1)).save(any());
    }

    // ═══════════════════════════════════════════════
    //  getMyProfile
    // ═══════════════════════════════════════════════

    @Test
    void getMyProfile_existingCompany_returnsResponse() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "MyCompany", CompanyStatus.APPROVED)));

        CompanyResponse response = companyService.getMyProfile();

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    void getMyProfile_returnsCorrectName() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "XYZ Corp", CompanyStatus.APPROVED)));

        CompanyResponse response = companyService.getMyProfile();

        assertThat(response.getName()).isEqualTo("XYZ Corp");
    }

    @Test
    void getMyProfile_companyNotFound_throwsResourceNotFoundException() {
        TenantContext.set(99L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getMyProfile())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMyProfile_usesCompanyIdFromTenantContext() {
        TenantContext.set(7L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(7L)).thenReturn(Optional.of(company(7L, "Corp", CompanyStatus.APPROVED)));

        companyService.getMyProfile();

        verify(companyRepository).findById(7L);
    }

    @Test
    void getMyProfile_neverSaves() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.APPROVED)));

        companyService.getMyProfile();

        verify(companyRepository, never()).save(any());
    }

    @Test
    void getMyProfile_returnsCorrectStatus() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.SUSPENDED)));

        CompanyResponse response = companyService.getMyProfile();

        assertThat(response.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void getMyProfile_approvedCompany_returnsApprovedStatus() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.APPROVED)));

        CompanyResponse response = companyService.getMyProfile();

        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void getMyProfile_noUserRepositoryInteraction() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.APPROVED)));

        companyService.getMyProfile();

        verifyNoInteractions(userRepository);
    }

    @Test
    void getMyProfile_noNotificationSent() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.APPROVED)));

        companyService.getMyProfile();

        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getMyProfile_differentCompanyIds_eachFetchedCorrectly() {
        for (long id = 1; id <= 5; id++) {
            TenantContext.set(id, 1L, "COMPANY_ADMIN", "COMPANY");
            when(companyRepository.findById(id)).thenReturn(Optional.of(company(id, "Corp" + id, CompanyStatus.APPROVED)));

            CompanyResponse response = companyService.getMyProfile();
            assertThat(response.getId()).isEqualTo(id);
        }
    }

    @Test
    void getMyProfile_findsCorrectCompanyByTenantId() {
        TenantContext.set(10L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company(10L, "Right Corp", CompanyStatus.APPROVED)));

        CompanyResponse response = companyService.getMyProfile();

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    void getMyProfile_callsRepositoryOnce() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.APPROVED)));

        companyService.getMyProfile();

        verify(companyRepository, times(1)).findById(5L);
    }

    @Test
    void getMyProfile_pendingCompany_returnsPendingStatus() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.PENDING)));

        CompanyResponse response = companyService.getMyProfile();

        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getMyProfile_noPasswordEncoderInteraction() {
        TenantContext.set(5L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company(5L, "Corp", CompanyStatus.APPROVED)));

        companyService.getMyProfile();

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getMyProfile_notFound_neverCallsOtherMethods() {
        TenantContext.set(99L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getMyProfile());
        verifyNoInteractions(notificationEventProducer);
        verifyNoInteractions(userRepository);
    }

    // ═══════════════════════════════════════════════
    //  updateProfile
    // ═══════════════════════════════════════════════

    @Test
    void updateProfile_updatesContactPhone() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();
        req.setContactPhone("+23480000001");

        companyService.updateProfile(req);

        assertThat(c.getContactPhone()).isEqualTo("+23480000001");
    }

    @Test
    void updateProfile_updatesAddress() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();
        req.setAddress("123 Fleet Street, Lagos");

        companyService.updateProfile(req);

        assertThat(c.getAddress()).isEqualTo("123 Fleet Street, Lagos");
    }

    @Test
    void updateProfile_updatesLogoUrl() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();
        req.setLogoUrl("https://cdn.io/logo.png");

        companyService.updateProfile(req);

        assertThat(c.getLogoUrl()).isEqualTo("https://cdn.io/logo.png");
    }

    @Test
    void updateProfile_nullFields_notUpdated() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        c.setContactPhone("existing_phone");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();

        companyService.updateProfile(req);

        assertThat(c.getContactPhone()).isEqualTo("existing_phone");
    }

    @Test
    void updateProfile_notFound_throwsResourceNotFoundException() {
        TenantContext.set(99L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.updateProfile(new CompanyUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_savesUpdatedCompany() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        var req = new CompanyUpdateRequest();
        req.setContactPhone("0800000001");

        companyService.updateProfile(req);

        verify(companyRepository, times(1)).save(c);
    }

    @Test
    void updateProfile_returnsUpdatedResponse() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();
        req.setAddress("New Address");

        CompanyResponse response = companyService.updateProfile(req);

        assertThat(response).isNotNull();
    }

    @Test
    void updateProfile_notFound_neverSaves() {
        TenantContext.set(99L, 1L, "COMPANY_ADMIN", "COMPANY");
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.updateProfile(new CompanyUpdateRequest()));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void updateProfile_noNotificationSent() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        var req = new CompanyUpdateRequest();
        req.setAddress("new address");

        companyService.updateProfile(req);

        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void updateProfile_noUserRepositoryInteraction() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.updateProfile(new CompanyUpdateRequest());

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateProfile_allFieldsUpdated() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();
        req.setContactPhone("+23491234567");
        req.setAddress("Lagos");
        req.setLogoUrl("https://logo.url");

        companyService.updateProfile(req);

        assertThat(c.getContactPhone()).isEqualTo("+23491234567");
        assertThat(c.getAddress()).isEqualTo("Lagos");
        assertThat(c.getLogoUrl()).isEqualTo("https://logo.url");
    }

    @Test
    void updateProfile_onlyContactPhoneSet_otherFieldsUntouched() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        c.setAddress("original address");
        c.setLogoUrl("original logo");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new CompanyUpdateRequest();
        req.setContactPhone("+2348000000001");

        companyService.updateProfile(req);

        assertThat(c.getAddress()).isEqualTo("original address");
        assertThat(c.getLogoUrl()).isEqualTo("original logo");
    }

    @Test
    void updateProfile_emptyRequest_returnsCurrentData() {
        TenantContext.set(1L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        c.setContactPhone("existing");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanyResponse response = companyService.updateProfile(new CompanyUpdateRequest());

        assertThat(response).isNotNull();
    }

    @Test
    void updateProfile_usesCompanyIdFromTenantContext() {
        TenantContext.set(15L, 1L, "COMPANY_ADMIN", "COMPANY");
        Company c = company(15L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(15L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.updateProfile(new CompanyUpdateRequest());

        verify(companyRepository).findById(15L);
    }

    // ═══════════════════════════════════════════════
    //  getAllCompanies
    // ═══════════════════════════════════════════════

    @Test
    void getAllCompanies_returnsAllCompanies() {
        when(companyRepository.findAll()).thenReturn(List.of(
                company(1L, "Corp A", CompanyStatus.APPROVED),
                company(2L, "Corp B", CompanyStatus.PENDING)));

        List<CompanyResponse> result = companyService.getAllCompanies();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllCompanies_returnsEmptyListWhenNone() {
        when(companyRepository.findAll()).thenReturn(List.of());

        assertThat(companyService.getAllCompanies()).isEmpty();
    }

    @Test
    void getAllCompanies_mapsNamesCorrectly() {
        when(companyRepository.findAll()).thenReturn(List.of(company(1L, "Corp A", CompanyStatus.APPROVED)));

        List<CompanyResponse> result = companyService.getAllCompanies();

        assertThat(result.get(0).getName()).isEqualTo("Corp A");
    }

    @Test
    void getAllCompanies_mapsStatusCorrectly() {
        when(companyRepository.findAll()).thenReturn(List.of(company(1L, "Corp", CompanyStatus.SUSPENDED)));

        List<CompanyResponse> result = companyService.getAllCompanies();

        assertThat(result.get(0).getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void getAllCompanies_callsFindAllExactlyOnce() {
        when(companyRepository.findAll()).thenReturn(List.of());

        companyService.getAllCompanies();

        verify(companyRepository, times(1)).findAll();
    }

    @Test
    void getAllCompanies_neverSaves() {
        when(companyRepository.findAll()).thenReturn(List.of());

        companyService.getAllCompanies();

        verify(companyRepository, never()).save(any());
    }

    @Test
    void getAllCompanies_noNotificationSent() {
        when(companyRepository.findAll()).thenReturn(List.of());

        companyService.getAllCompanies();

        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAllCompanies_multipleStatuses_allMapped() {
        when(companyRepository.findAll()).thenReturn(List.of(
                company(1L, "A", CompanyStatus.APPROVED),
                company(2L, "B", CompanyStatus.PENDING),
                company(3L, "C", CompanyStatus.REJECTED)));

        List<CompanyResponse> result = companyService.getAllCompanies();

        assertThat(result).extracting(CompanyResponse::getStatus)
                .containsExactlyInAnyOrder("APPROVED", "PENDING", "REJECTED");
    }

    @Test
    void getAllCompanies_idsCorrectlyMapped() {
        when(companyRepository.findAll()).thenReturn(List.of(
                company(10L, "A", CompanyStatus.APPROVED),
                company(20L, "B", CompanyStatus.APPROVED)));

        List<CompanyResponse> result = companyService.getAllCompanies();

        assertThat(result).extracting(CompanyResponse::getId).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void getAllCompanies_noUserRepositoryInteraction() {
        when(companyRepository.findAll()).thenReturn(List.of());

        companyService.getAllCompanies();

        verifyNoInteractions(userRepository);
    }

    @Test
    void getAllCompanies_largeList_allReturned() {
        List<Company> companies = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            companies.add(company((long) i, "Corp " + i, CompanyStatus.APPROVED));
        }
        when(companyRepository.findAll()).thenReturn(companies);

        assertThat(companyService.getAllCompanies()).hasSize(100);
    }

    @Test
    void getAllCompanies_singleCompany_returnsListOfOne() {
        when(companyRepository.findAll()).thenReturn(List.of(company(1L, "Solo Corp", CompanyStatus.APPROVED)));

        assertThat(companyService.getAllCompanies()).hasSize(1);
    }

    @Test
    void getAllCompanies_noPasswordEncoderInteraction() {
        when(companyRepository.findAll()).thenReturn(List.of());

        companyService.getAllCompanies();

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getAllCompanies_findAllNotFindById() {
        when(companyRepository.findAll()).thenReturn(List.of());

        companyService.getAllCompanies();

        verify(companyRepository, never()).findById(any());
    }

    // ═══════════════════════════════════════════════
    //  approveCompany
    // ═══════════════════════════════════════════════

    @Test
    void approveCompany_setsStatusToApproved() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        companyService.approveCompany(1L);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.APPROVED);
    }

    @Test
    void approveCompany_setsApprovedAt() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        companyService.approveCompany(1L);

        assertThat(c.getApprovedAt()).isNotNull();
    }

    @Test
    void approveCompany_activatesAllCompanyUsers() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        User u1 = makeUser(1L, Role.COMPANY_ADMIN, c, false);
        User u2 = makeUser(2L, Role.FLEET_MANAGER, c, false);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of(u1, u2));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.approveCompany(1L);

        assertThat(u1.isActive()).isTrue();
        assertThat(u2.isActive()).isTrue();
    }

    @Test
    void approveCompany_sendsNotification() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        companyService.approveCompany(1L);

        verify(notificationEventProducer, times(1)).publish(any());
    }

    @Test
    void approveCompany_notFound_throwsResourceNotFoundException() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.approveCompany(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approveCompany_notFound_neverSaves() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.approveCompany(99L));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void approveCompany_returnsApprovedResponse() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        CompanyResponse response = companyService.approveCompany(1L);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approveCompany_noUsersForCompany_stillApproves() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        assertThatCode(() -> companyService.approveCompany(1L)).doesNotThrowAnyException();
    }

    @Test
    void approveCompany_eachUserSavedAfterActivation() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        User u = makeUser(1L, Role.COMPANY_ADMIN, c, false);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of(u));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.approveCompany(1L);

        verify(userRepository, times(1)).save(u);
    }

    @Test
    void approveCompany_notificationContainsCompanyEmail() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        c.setEmail("corp@test.com");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        companyService.approveCompany(1L);

        var captor = ArgumentCaptor.forClass(com.fleetops.core.module.kafka.event.NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("corp@test.com");
    }

    @Test
    void approveCompany_notificationTypeIsApproved() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        companyService.approveCompany(1L);

        var captor = ArgumentCaptor.forClass(com.fleetops.core.module.kafka.event.NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("COMPANY_APPROVED");
    }

    @Test
    void approveCompany_multipleUsersAllActivated() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        List<User> users = List.of(
                makeUser(1L, Role.COMPANY_ADMIN, c, false),
                makeUser(2L, Role.FLEET_MANAGER, c, false),
                makeUser(3L, Role.FIELD_STAFF, c, false));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(users);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.approveCompany(1L);

        users.forEach(u -> assertThat(u.isActive()).isTrue());
        verify(userRepository, times(3)).save(any());
    }

    @Test
    void approveCompany_companyIdUsedForLookup() {
        Company c = company(42L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(42L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(42L)).thenReturn(List.of());

        companyService.approveCompany(42L);

        verify(companyRepository).findById(42L);
        verify(userRepository).findByCompanyId(42L);
    }

    @Test
    void approveCompany_approvedAtIsRecent() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of());

        companyService.approveCompany(1L);

        assertThat(c.getApprovedAt()).isAfterOrEqualTo(java.time.LocalDateTime.now().minusSeconds(5));
    }

    // ═══════════════════════════════════════════════
    //  rejectCompany
    // ═══════════════════════════════════════════════

    @Test
    void rejectCompany_setsStatusToRejected() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("Not eligible"));

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.REJECTED);
    }

    @Test
    void rejectCompany_setsRejectionReason() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("Incomplete documents"));

        assertThat(c.getRejectionReason()).isEqualTo("Incomplete documents");
    }

    @Test
    void rejectCompany_sendsNotification() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("Reason"));

        verify(notificationEventProducer, times(1)).publish(any());
    }

    @Test
    void rejectCompany_notFound_throwsResourceNotFoundException() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.rejectCompany(99L, rejectionRequest("reason")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectCompany_notFound_neverSaves() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.rejectCompany(99L, rejectionRequest("reason")));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void rejectCompany_returnsRejectedStatus() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        CompanyResponse response = companyService.rejectCompany(1L, rejectionRequest("Reason"));

        assertThat(response.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void rejectCompany_notificationTypeIsRejected() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("reason"));

        var captor = ArgumentCaptor.forClass(com.fleetops.core.module.kafka.event.NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("COMPANY_REJECTED");
    }

    @Test
    void rejectCompany_savesCompanyOnce() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("Reason"));

        verify(companyRepository, times(1)).save(c);
    }

    @Test
    void rejectCompany_noUserActivationOccurs() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("reason"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectCompany_rejectionReasonInNotification() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("Fraudulent activity"));

        var captor = ArgumentCaptor.forClass(com.fleetops.core.module.kafka.event.NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Fraudulent activity");
    }

    @Test
    void rejectCompany_longRejectionReason_handledCorrectly() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);
        String longReason = "reason ".repeat(50);

        assertThatCode(() -> companyService.rejectCompany(1L, rejectionRequest(longReason)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectCompany_differentRejectionReasons_eachStored() {
        String[] reasons = {"Too new", "Incomplete docs", "Fraud suspected"};
        for (String reason : reasons) {
            Company c = company(1L, "Corp", CompanyStatus.PENDING);
            when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
            when(companyRepository.save(any())).thenReturn(c);

            companyService.rejectCompany(1L, rejectionRequest(reason));

            assertThat(c.getRejectionReason()).isEqualTo(reason);
        }
    }

    @Test
    void rejectCompany_noPasswordEncoderInteraction() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("reason"));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rejectCompany_notificationSentToCompanyEmail() {
        Company c = company(1L, "Corp", CompanyStatus.PENDING);
        c.setEmail("corp@acme.com");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.rejectCompany(1L, rejectionRequest("reason"));

        var captor = ArgumentCaptor.forClass(com.fleetops.core.module.kafka.event.NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("corp@acme.com");
    }

    // ═══════════════════════════════════════════════
    //  suspendCompany
    // ═══════════════════════════════════════════════

    @Test
    void suspendCompany_setsStatusToSuspended() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.suspendCompany(1L);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void suspendCompany_notFound_throwsResourceNotFoundException() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.suspendCompany(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void suspendCompany_savesCompany() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.suspendCompany(1L);

        verify(companyRepository, times(1)).save(c);
    }

    @Test
    void suspendCompany_returnsSuspendedStatus() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        CompanyResponse response = companyService.suspendCompany(1L);

        assertThat(response.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspendCompany_noNotificationSent() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.suspendCompany(1L);

        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void suspendCompany_noUserRepositoryInteraction() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.suspendCompany(1L);

        verifyNoInteractions(userRepository);
    }

    @Test
    void suspendCompany_notFound_neverSaves() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.suspendCompany(99L));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void suspendCompany_previouslyApproved_nowSuspended() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        companyService.suspendCompany(1L);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void suspendCompany_correctIdPassedToRepository() {
        Company c = company(55L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(55L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.suspendCompany(55L);

        verify(companyRepository).findById(55L);
    }

    @Test
    void suspendCompany_noPasswordEncoderInteraction() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.suspendCompany(1L);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void suspendCompany_returnsNonNullResponse() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        assertThat(companyService.suspendCompany(1L)).isNotNull();
    }

    @Test
    void suspendCompany_responseIdMatchesInput() {
        Company c = company(22L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(22L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        CompanyResponse response = companyService.suspendCompany(22L);

        assertThat(response.getId()).isEqualTo(22L);
    }

    @Test
    void suspendCompany_statusChangedBeforeSave() {
        Company c = company(1L, "Corp", CompanyStatus.APPROVED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(inv -> {
            Company saved = inv.getArgument(0);
            assertThat(saved.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
            return saved;
        });

        companyService.suspendCompany(1L);
    }

    // ═══════════════════════════════════════════════
    //  reactivateCompany
    // ═══════════════════════════════════════════════

    @Test
    void reactivateCompany_setsStatusToApproved() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(1L);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.APPROVED);
    }

    @Test
    void reactivateCompany_notFound_throwsResourceNotFoundException() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.reactivateCompany(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivateCompany_returnsApprovedStatus() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        CompanyResponse response = companyService.reactivateCompany(1L);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void reactivateCompany_savesCompany() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(1L);

        verify(companyRepository, times(1)).save(c);
    }

    @Test
    void reactivateCompany_noNotificationSent() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(1L);

        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void reactivateCompany_noUserRepositoryInteraction() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(1L);

        verifyNoInteractions(userRepository);
    }

    @Test
    void reactivateCompany_notFound_neverSaves() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.reactivateCompany(99L));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void reactivateCompany_fromRejectedStatus_setsApproved() {
        Company c = company(1L, "Corp", CompanyStatus.REJECTED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(1L);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.APPROVED);
    }

    @Test
    void reactivateCompany_correctIdPassedToRepository() {
        Company c = company(77L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(77L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(77L);

        verify(companyRepository).findById(77L);
    }

    @Test
    void reactivateCompany_noPasswordEncoderInteraction() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        companyService.reactivateCompany(1L);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void reactivateCompany_responseIdMatchesInput() {
        Company c = company(33L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(33L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        CompanyResponse response = companyService.reactivateCompany(33L);

        assertThat(response.getId()).isEqualTo(33L);
    }

    @Test
    void reactivateCompany_responseNameIsCorrect() {
        Company c = company(1L, "Restored Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenReturn(c);

        CompanyResponse response = companyService.reactivateCompany(1L);

        assertThat(response.getName()).isEqualTo("Restored Corp");
    }

    @Test
    void reactivateCompany_statusChangedBeforeSave() {
        Company c = company(1L, "Corp", CompanyStatus.SUSPENDED);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(companyRepository.save(any())).thenAnswer(inv -> {
            Company saved = inv.getArgument(0);
            assertThat(saved.getStatus()).isEqualTo(CompanyStatus.APPROVED);
            return saved;
        });

        companyService.reactivateCompany(1L);
    }

    // ═══════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════

    private Company company(Long id, String name, CompanyStatus status) {
        return Company.builder().id(id).name(name).email(name.toLowerCase().replace(" ", "") + "@corp.com")
                .status(status).build();
    }

    private CompanyRegistrationRequest registrationRequest(String name, String email, String adminEmail) {
        var req = new CompanyRegistrationRequest();
        req.setName(name);
        req.setEmail(email);
        req.setAdminName("Admin");
        req.setAdminEmail(adminEmail);
        req.setAdminPassword("Admin@1234");
        req.setContactPhone("+2348000000001");
        req.setAddress("Lagos, Nigeria");
        return req;
    }

    private RejectionRequest rejectionRequest(String reason) {
        var req = new RejectionRequest();
        req.setReason(reason);
        return req;
    }

    private User makeUser(Long id, Role role, Company company, boolean active) {
        return User.builder().id(id).name("User" + id).email("user" + id + "@test.com")
                .password("hash").role(role).userType(UserType.COMPANY)
                .company(company).active(active).build();
    }

    private void setupRegisterMocks(String companyEmail, String companyName, String adminEmail) {
        when(companyRepository.existsByEmail(companyEmail)).thenReturn(false);
        when(companyRepository.existsByName(companyName)).thenReturn(false);
        when(userRepository.existsByEmail(adminEmail)).thenReturn(false);
    }
}
