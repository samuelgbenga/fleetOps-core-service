package com.fleetops.core.module.company.service.impl;

import com.fleetops.core.module.company.dto.*;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.model.CompanyStatus;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.company.service.CompanyService;
import com.fleetops.core.module.kafka.event.CompanyApprovedEvent;
import com.fleetops.core.module.kafka.event.CompanyRegisteredEvent;
import com.fleetops.core.module.kafka.event.CompanyRejectedEvent;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional
    public CompanyResponse register(CompanyRegistrationRequest request) {
        if (companyRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Company email already registered");
        }
        if (companyRepository.existsByName(request.getName())) {
            throw new ConflictException("Company name already taken");
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new ConflictException("Admin email already registered");
        }

        Company company = companyRepository.save(Company.builder()
                .name(request.getName())
                .email(request.getEmail())
                .contactPhone(request.getContactPhone())
                .address(request.getAddress())
                .status(CompanyStatus.PENDING)
                .build());

        userRepository.save(User.builder()
                .company(company)
                .name(request.getAdminName())
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .role(Role.COMPANY_ADMIN)
                .userType(UserType.COMPANY)
                .active(false)
                .build());

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(request.getAdminEmail())
                .recipientName(request.getAdminName())
                .subject("Company Registration Received")
                .message("Your registration for " + request.getName() + " is pending review.")
                .type("COMPANY_REGISTERED")
                .occurredAt(LocalDateTime.now())
                .build());

        userRepository.findByRole(Role.PLATFORM_ADMIN).forEach(admin ->
                notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(admin.getEmail())
                        .recipientName(admin.getName())
                        .subject("New Company Registration: " + company.getName())
                        .message(String.format(
                                "Hi %s,\n\nA new company has registered and is awaiting approval.\n\nCompany: %s\nEmail: %s\n\nFleetOps System",
                                admin.getName(), company.getName(), company.getEmail()))
                        .type("COMPANY_REGISTERED_ADMIN")
                        .occurredAt(LocalDateTime.now())
                        .build())
        );

        return CompanyResponse.from(company);
    }

    @Override
    public CompanyResponse getMyProfile() {
        Long companyId = TenantContext.getCompanyId();
        return CompanyResponse.from(companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found")));
    }

    @Override
    @Transactional
    public CompanyResponse updateProfile(CompanyUpdateRequest request) {
        Long companyId = TenantContext.getCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        if (request.getContactPhone() != null) company.setContactPhone(request.getContactPhone());
        if (request.getAddress() != null) company.setAddress(request.getAddress());
        if (request.getLogoUrl() != null) company.setLogoUrl(request.getLogoUrl());
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Override
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAll().stream().map(CompanyResponse::from).toList();
    }

    @Override
    public CompanyResponse getCompanyById(Long id) {
        return CompanyResponse.from(companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id)));
    }

    @Override
    @Transactional
    public CompanyResponse approveCompany(Long id) {
        Company company = getOrThrow(id);
        company.setStatus(CompanyStatus.APPROVED);
        company.setApprovedAt(LocalDateTime.now());
        companyRepository.save(company);

        userRepository.findByCompanyId(id).forEach(u -> {
            u.setActive(true);
            userRepository.save(u);
        });

        userRepository.findByCompanyIdAndRole(id, Role.COMPANY_ADMIN).ifPresent(admin ->
                notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(admin.getEmail())
                        .recipientName(admin.getName())
                        .subject("Company Approved!")
                        .message("Congratulations! " + company.getName() + " has been approved on FleetOps.")
                        .type("COMPANY_APPROVED")
                        .occurredAt(LocalDateTime.now())
                        .build())
        );

        return CompanyResponse.from(company);
    }

    @Override
    @Transactional
    public CompanyResponse rejectCompany(Long id, RejectionRequest request) {
        Company company = getOrThrow(id);
        company.setStatus(CompanyStatus.REJECTED);
        company.setRejectionReason(request.getReason());
        companyRepository.save(company);

        userRepository.findByCompanyIdAndRole(id, Role.COMPANY_ADMIN).ifPresent(admin ->
                notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(admin.getEmail())
                        .recipientName(admin.getName())
                        .subject("Company Registration Rejected")
                        .message("Unfortunately, " + company.getName() + " was rejected. Reason: " + request.getReason())
                        .type("COMPANY_REJECTED")
                        .occurredAt(LocalDateTime.now())
                        .build())
        );

        return CompanyResponse.from(company);
    }

    @Override
    @Transactional
    public CompanyResponse suspendCompany(Long id) {
        Company company = getOrThrow(id);
        company.setStatus(CompanyStatus.SUSPENDED);
        companyRepository.save(company);

        userRepository.findByCompanyIdAndRole(id, Role.COMPANY_ADMIN).ifPresent(admin ->
                notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(admin.getEmail())
                        .recipientName(admin.getName())
                        .subject("Company Account Suspended")
                        .message("Your company " + company.getName() + " has been suspended on FleetOps. Please contact support for more information.")
                        .type("COMPANY_SUSPENDED")
                        .occurredAt(LocalDateTime.now())
                        .build())
        );

        return CompanyResponse.from(company);
    }

    @Override
    @Transactional
    public CompanyResponse reactivateCompany(Long id) {
        Company company = getOrThrow(id);
        company.setStatus(CompanyStatus.APPROVED);
        companyRepository.save(company);

        userRepository.findByCompanyIdAndRole(id, Role.COMPANY_ADMIN).ifPresent(admin ->
                notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(admin.getEmail())
                        .recipientName(admin.getName())
                        .subject("Company Account Reactivated")
                        .message("Good news! Your company " + company.getName() + " has been reactivated on FleetOps.")
                        .type("COMPANY_REACTIVATED")
                        .occurredAt(LocalDateTime.now())
                        .build())
        );

        return CompanyResponse.from(company);
    }

    private Company getOrThrow(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }
}
