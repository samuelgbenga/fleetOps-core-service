package com.fleetops.core.module.auth.service.impl;

import com.fleetops.core.module.auth.dto.AuthResponse;
import com.fleetops.core.module.auth.dto.ChangePasswordRequest;
import com.fleetops.core.module.auth.dto.LoginRequest;
import com.fleetops.core.module.auth.service.AuthService;
import com.fleetops.core.module.auth.util.JwtUtil;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user))
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .userType(user.getUserType().name())
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .build();
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
