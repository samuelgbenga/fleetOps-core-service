package com.fleetops.core.controller;

import com.fleetops.core.module.auth.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends BaseControllerIntegrationTest {

    // POST /api/public/auth/login

    @Test
    void login_validCredentials_returns200() throws Exception {
        var response = AuthResponse.builder()
                .token("jwt.token.here")
                .userId(10L)
                .name("Admin User")
                .email("admin@co.com")
                .role("COMPANY_ADMIN")
                .userType("COMPANY")
                .companyId(COMPANY_ID)
                .build();
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@co.com","password":"Admin@1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.role").value("COMPANY_ADMIN"));
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"Admin@1234"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@co.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // PATCH /api/auth/change-password

    @Test
    void changePassword_authenticated_returns204() throws Exception {
        mockMvc.perform(patch("/api/auth/change-password")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Old@1234","newPassword":"New@1234"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Old@1234","newPassword":"New@1234"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_weakNewPassword_returns400() throws Exception {
        mockMvc.perform(patch("/api/auth/change-password")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Old@1234","newPassword":"weakpassword"}
                                """))
                .andExpect(status().isBadRequest());
    }
}