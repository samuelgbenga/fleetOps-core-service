package com.fleetops.core.controller;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest extends BaseControllerIntegrationTest {

    private UserResponse sampleUser(Long id, String role) {
        return UserResponse.builder()
                .id(id).companyId(COMPANY_ID).name("Test User").email("user@test.com")
                .role(role).userType("COMPANY").active(true).build();
    }

    private UserResponse sampleCrewUser() {
        return UserResponse.builder()
                .id(13L).name("Crew Member").email("crew@example.com")
                .role("MAINTENANCE_CREW").userType("PLATFORM").active(true).build();
    }

    private MediaResponse sampleMedia() {
        return MediaResponse.builder()
                .id(1L).publicId("pub123").url("https://example.com/avatar.jpg")
                .ownerType("USER").ownerId(10L).build();
    }

    // ── CompanyUserController ──────────────────────────────────────────────

    // POST /api/company/users

    @Test
    void createCompanyUser_companyAdmin_returns201() throws Exception {
        when(userService.createCompanyUser(any())).thenReturn(sampleUser(20L, "FIELD_STAFF"));

        mockMvc.perform(post("/api/company/users")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"New Staff","email":"newstaff@co.com",
                                  "password":"Admin@1234","role":"FIELD_STAFF"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FIELD_STAFF"));
    }

    @Test
    void createCompanyUser_fleetManager_returns403() throws Exception {
        mockMvc.perform(post("/api/company/users")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Staff","email":"newstaff@co.com","password":"Admin@1234","role":"FIELD_STAFF"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCompanyUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/company/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Staff","email":"newstaff@co.com","password":"Admin@1234","role":"FIELD_STAFF"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/company/users

    @Test
    void getCompanyUsers_companyAdmin_returns200() throws Exception {
        when(userService.getCompanyUsers()).thenReturn(List.of(sampleUser(11L, "FLEET_MANAGER")));

        mockMvc.perform(get("/api/company/users")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("FLEET_MANAGER"));
    }

    @Test
    void getCompanyUsers_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/company/users")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/company/users/{id}

    @Test
    void getCompanyUserById_companyAdmin_returns200() throws Exception {
        when(userService.getCompanyUserById(11L)).thenReturn(sampleUser(11L, "FLEET_MANAGER"));

        mockMvc.perform(get("/api/company/users/11")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void getCompanyUserById_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/company/users/11")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/company/users/{id}/deactivate

    @Test
    void deactivateUser_companyAdmin_returns204() throws Exception {
        mockMvc.perform(patch("/api/company/users/11/deactivate")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateUser_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/company/users/11/deactivate")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/company/users/{id}/reactivate

    @Test
    void reactivateUser_companyAdmin_returns204() throws Exception {
        mockMvc.perform(patch("/api/company/users/11/reactivate")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isNoContent());
    }

    // PATCH /api/company/users/{id}/reset-password

    @Test
    void resetUserPassword_companyAdmin_returns204() throws Exception {
        mockMvc.perform(patch("/api/company/users/11/reset-password")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Reset@1234"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetUserPassword_weakPassword_returns400() throws Exception {
        mockMvc.perform(patch("/api/company/users/11/reset-password")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"weakpass"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // PATCH /api/admin/users/{id}/media

    @Test
    void uploadUserMedia_companyAdmin_returns200() throws Exception {
        when(mediaService.upload(any())).thenReturn(sampleMedia());

        mockMvc.perform(patch("/api/admin/users/11/media")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicId":"pub123","url":"https://example.com/avatar.jpg",
                                  "ownerType":"USER","ownerId":11
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("pub123"));
    }

    @Test
    void uploadUserMedia_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/users/11/media")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicId":"pub123","url":"https://example.com/avatar.jpg","ownerType":"USER","ownerId":11}
                                """))
                .andExpect(status().isForbidden());
    }

    // DELETE /api/admin/users/{id}/media

    @Test
    void deleteUserMedia_companyAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/users/11/media")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUserMedia_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/admin/users/11/media"))
                .andExpect(status().isUnauthorized());
    }

    // ── PlatformCrewController ─────────────────────────────────────────────

    // POST /api/platform/crew

    @Test
    void createCrewMember_platformAdmin_returns201() throws Exception {
        when(userService.createCrewMember(any())).thenReturn(sampleCrewUser());

        mockMvc.perform(post("/api/platform/crew")
                        .header("Authorization", platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"New Crew","email":"newcrew@platform.com",
                                  "password":"Admin@1234","role":"MAINTENANCE_CREW"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MAINTENANCE_CREW"));
    }

    @Test
    void createCrewMember_companyAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/platform/crew")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Crew","email":"newcrew@platform.com","password":"Admin@1234","role":"MAINTENANCE_CREW"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCrewMember_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/platform/crew")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Crew","email":"newcrew@platform.com","password":"Admin@1234","role":"MAINTENANCE_CREW"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/platform/crew

    @Test
    void getAllCrew_platformAdmin_returns200() throws Exception {
        when(userService.getAllCrew()).thenReturn(List.of(sampleCrewUser()));

        mockMvc.perform(get("/api/platform/crew")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("MAINTENANCE_CREW"));
    }

    @Test
    void getAllCrew_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/crew")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/platform/crew/{id}

    @Test
    void getCrewById_platformAdmin_returns200() throws Exception {
        when(userService.getCrewById(13L)).thenReturn(sampleCrewUser());

        mockMvc.perform(get("/api/platform/crew/13")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(13));
    }

    // DELETE /api/platform/crew/{id}

    @Test
    void deleteCrewMember_platformAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/platform/crew/13")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCrewMember_companyAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/platform/crew/13")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/platform/crew/{id}/performance

    @Test
    void getCrewPerformance_platformAdmin_returns200() throws Exception {
        when(userService.getCrewById(13L)).thenReturn(sampleCrewUser());

        mockMvc.perform(get("/api/platform/crew/13/performance")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk());
    }

    // ── ProfileController ──────────────────────────────────────────────────

    // GET /api/users/me

    @Test
    void getMyProfile_authenticated_returns200() throws Exception {
        when(userService.getMe()).thenReturn(sampleUser(10L, "COMPANY_ADMIN"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getMyProfile_fieldStaff_returns200() throws Exception {
        when(userService.getMe()).thenReturn(sampleUser(12L, "FIELD_STAFF"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMyProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/users/me

    @Test
    void updateMyProfile_authenticated_returns200() throws Exception {
        when(userService.updateMe(any())).thenReturn(sampleUser(10L, "COMPANY_ADMIN"));

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Name"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateMyProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Name"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/users/me/media

    @Test
    void uploadProfileMedia_authenticated_returns200() throws Exception {
        when(mediaService.upload(any())).thenReturn(sampleMedia());

        mockMvc.perform(patch("/api/users/me/media")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicId":"pub123","url":"https://example.com/avatar.jpg",
                                  "ownerType":"USER","ownerId":10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("pub123"));
    }

    @Test
    void uploadProfileMedia_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/me/media")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicId":"pub123","url":"https://example.com/avatar.jpg","ownerType":"USER","ownerId":10}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // DELETE /api/users/me/media

    @Test
    void deleteProfileMedia_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/me/media")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProfileMedia_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/users/me/media"))
                .andExpect(status().isUnauthorized());
    }
}
