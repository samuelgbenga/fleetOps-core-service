package com.fleetops.core.user.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.media.dto.MediaRequest;
import com.fleetops.core.media.dto.MediaResponse;
import com.fleetops.core.media.entity.Media;
import com.fleetops.core.user.dto.CreateUserRequest;
import com.fleetops.core.user.dto.ResetPasswordRequest;
import com.fleetops.core.user.dto.UpdateProfileRequest;
import com.fleetops.core.user.dto.UserResponse;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationEventProducer notificationEventProducer;

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        User saved = userRepository.save(user);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(saved.getEmail())
                .recipientName(saved.getName())
                .subject("Welcome to FleetOps — Your Account Details")
                .message(String.format(
                        "Hi %s,\n\n" +
                        "Your FleetOps account has been created by an administrator.\n\n" +
                        "Your login credentials:\n" +
                        "  Email:    %s\n" +
                        "  Password: %s\n" +
                        "  Role:     %s\n\n" +
                        "⚠ For security, please change your password immediately after your first login:\n" +
                        "  PATCH /api/auth/change-password\n\n" +
                        "FleetOps System",
                        saved.getName(), saved.getEmail(), request.getPassword(), saved.getRole()))
                .type("ACCOUNT_CREATED")
                .occurredAt(LocalDateTime.now())
                .build());

        return UserResponse.from(saved);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ── Self-service profile endpoints ────────────────────────────────────────

    public UserResponse getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public UserResponse updateMyProfile(UpdateProfileRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setName(request.getName());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public MediaResponse setMyProfileMedia(MediaRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setProfileMedia(Media.builder()
                .publicId(request.getPublicId())
                .url(request.getUrl())
                .build());
        userRepository.save(user);
        return MediaResponse.from(user.getProfileMedia());
    }

    @Transactional
    public void removeMyProfileMedia() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getProfileMedia() == null) {
            throw new ConflictException("You have no profile media to remove");
        }
        user.setProfileMedia(null);
        userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (!user.isActive()) {
            throw new ConflictException("User account is already deactivated");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void reactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.isActive()) {
            throw new ConflictException("User account is already active");
        }
        user.setActive(true);
        userRepository.save(user);
    }
}
