package com.fleetops.core.module.user.service.impl;

import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.user.dto.*;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.user.service.UserService;
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
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationEventProducer notificationEventProducer;

    @Override
    public UserResponse getMe() {
        return UserResponse.from(currentUser());
    }

    @Override
    @Transactional
    public UserResponse updateMe(UpdateProfileRequest request) {
        User user = currentUser();
        if (request.getName() != null) user.setName(request.getName());
        if (request.getProfileImageUrl() != null) user.setProfileImageUrl(request.getProfileImageUrl());
        if (request.getProfileImageId() != null) user.setProfileImageId(request.getProfileImageId());
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse createCompanyUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }
        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        User user = userRepository.save(User.builder()
                .company(company)
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .userType(UserType.COMPANY)
                .available(request.getRole() == Role.FIELD_STAFF)
                .build());

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(user.getEmail())
                .recipientName(user.getName())
                .subject("Welcome to FleetOps!")
                .message("Your account has been created. Role: " + user.getRole().name())
                .type("USER_CREATED")
                .occurredAt(LocalDateTime.now())
                .build());

        return UserResponse.from(user);
    }

    @Override
    public List<UserResponse> getCompanyUsers() {
        return userRepository.findByCompanyId(TenantContext.getCompanyId())
                .stream().map(UserResponse::from).toList();
    }

    @Override
    public UserResponse getCompanyUserById(Long id) {
        return UserResponse.from(userRepository.findById(id)
                .filter(u -> u.getCompany() != null
                        && u.getCompany().getId().equals(TenantContext.getCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id)));
    }

    @Override
    @Transactional
    public void deactivateUser(Long id) {
        User user = getCompanyUserOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void reactivateUser(Long id) {
        User user = getCompanyUserOrThrow(id);
        user.setActive(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        User user = getCompanyUserOrThrow(id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse createCrewMember(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }
        User crew = userRepository.save(User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.MAINTENANCE_CREW)
                .userType(UserType.PLATFORM)
                .available(true)
                .build());

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(crew.getEmail())
                .recipientName(crew.getName())
                .subject("Welcome to FleetOps Maintenance Team!")
                .message("Your maintenance crew account has been created.")
                .type("USER_CREATED")
                .occurredAt(LocalDateTime.now())
                .build());

        return UserResponse.from(crew);
    }

    @Override
    public List<UserResponse> getAllCrew() {
        return userRepository.findByRoleAndCompanyId(Role.MAINTENANCE_CREW, TenantContext.getCompanyId())
                .stream().map(UserResponse::from).toList();
    }

    @Override
    public UserResponse getCrewById(Long id) {
        return UserResponse.from(userRepository.findById(id)
                .filter(u -> u.getRole() == Role.MAINTENANCE_CREW
                        && u.getCompany() != null
                        && u.getCompany().getId().equals(TenantContext.getCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found: " + id)));
    }

    @Override
    @Transactional
    public void deleteCrew(Long id) {
        User crew = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.MAINTENANCE_CREW
                        && u.getCompany() != null
                        && u.getCompany().getId().equals(TenantContext.getCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found: " + id));
        userRepository.delete(crew);
    }

    @Override
    @Transactional
    public void updateProfileImage(Long userId, String url, String publicId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setProfileImageUrl(url);
        user.setProfileImageId(publicId);
    }

    @Override
    @Transactional
    public void clearProfileImage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setProfileImageUrl(null);
        user.setProfileImageId(null);
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User getCompanyUserOrThrow(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getCompany() != null
                        && u.getCompany().getId().equals(TenantContext.getCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
