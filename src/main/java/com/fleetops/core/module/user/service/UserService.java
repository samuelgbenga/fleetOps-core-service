package com.fleetops.core.module.user.service;

import com.fleetops.core.module.user.dto.*;

import java.util.List;

public interface UserService {
    UserResponse getMe();
    UserResponse updateMe(UpdateProfileRequest request);
    UserResponse createCompanyUser(CreateUserRequest request);
    List<UserResponse> getCompanyUsers();
    UserResponse getCompanyUserById(Long id);
    void deactivateUser(Long id);
    void reactivateUser(Long id);
    void resetPassword(Long id, ResetPasswordRequest request);
    UserResponse createCrewMember(CreateUserRequest request);
    List<UserResponse> getAllCrew();
    List<UserResponse> getAvailableCrew();
    UserResponse getCrewById(Long id);
    void deleteCrew(Long id);
    void updateProfileImage(Long userId, String url, String publicId);
    void clearProfileImage(Long userId);
}
