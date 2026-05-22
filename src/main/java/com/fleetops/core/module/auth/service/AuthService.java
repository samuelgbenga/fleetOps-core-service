package com.fleetops.core.module.auth.service;

import com.fleetops.core.module.auth.dto.AuthResponse;
import com.fleetops.core.module.auth.dto.ChangePasswordRequest;
import com.fleetops.core.module.auth.dto.LoginRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    void changePassword(ChangePasswordRequest request);
}
