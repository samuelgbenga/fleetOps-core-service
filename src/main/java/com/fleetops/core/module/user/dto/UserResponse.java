package com.fleetops.core.module.user.dto;

import com.fleetops.core.module.user.model.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private Long companyId;
    private String name;
    private String email;
    private String role;
    private String userType;
    private boolean active;
    private Double averageRating;
    private Integer totalJobsCompleted;
    private Boolean available;
    private String profileImageUrl;
    private String profileImageId;
    private LocalDateTime createdAt;

    public static UserResponse from(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .companyId(u.getCompany() != null ? u.getCompany().getId() : null)
                .name(u.getName())
                .email(u.getEmail())
                .role(u.getRole().name())
                .userType(u.getUserType().name())
                .active(u.isActive())
                .averageRating(u.getAverageRating())
                .totalJobsCompleted(u.getTotalJobsCompleted())
                .available(u.getAvailable())
                .profileImageUrl(u.getProfileImageUrl())
                .profileImageId(u.getProfileImageId())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
