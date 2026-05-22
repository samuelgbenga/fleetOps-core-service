package com.fleetops.core.controller;

import com.fleetops.core.module.company.dto.CompanyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyControllerTest extends BaseControllerIntegrationTest {

    private CompanyResponse sampleCompany() {
        return CompanyResponse.builder()
                .id(COMPANY_ID).name("Test Co").email("test@co.com")
                .status("PENDING").build();
    }

    // ── CompanyController ──────────────────────────────────────────────────

    // POST /api/public/companies/register

    @Test
    void register_validRequest_returns201() throws Exception {
        when(companyService.register(any())).thenReturn(sampleCompany());

        mockMvc.perform(post("/api/public/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"FleetCo","email":"fleet@co.com",
                                  "adminName":"Admin","adminEmail":"admin@fleet.com",
                                  "adminPassword":"Admin@1234"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Co"));
    }

    @Test
    void register_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/public/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"FleetCo"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // GET /api/company/profile

    @Test
    void getProfile_companyAdmin_returns200() throws Exception {
        when(companyService.getMyProfile()).thenReturn(sampleCompany());

        mockMvc.perform(get("/api/company/profile")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID));
    }

    @Test
    void getProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/company/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/company/profile")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/company/profile

    @Test
    void updateProfile_companyAdmin_returns200() throws Exception {
        when(companyService.updateProfile(any())).thenReturn(sampleCompany());

        mockMvc.perform(patch("/api/company/profile")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactPhone":"08012345678","address":"Test Street"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/company/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/company/profile")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── PlatformCompanyController ──────────────────────────────────────────

    // GET /api/platform/companies

    @Test
    void listAllCompanies_platformAdmin_returns200() throws Exception {
        when(companyService.getAllCompanies()).thenReturn(List.of(sampleCompany()));

        mockMvc.perform(get("/api/platform/companies")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COMPANY_ID));
    }

    @Test
    void listAllCompanies_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/platform/companies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllCompanies_companyAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/companies")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/platform/companies/{id}

    @Test
    void getCompanyById_platformAdmin_returns200() throws Exception {
        when(companyService.getCompanyById(COMPANY_ID)).thenReturn(sampleCompany());

        mockMvc.perform(get("/api/platform/companies/" + COMPANY_ID)
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID));
    }

    // PATCH /api/platform/companies/{id}/approve

    @Test
    void approveCompany_platformAdmin_returns200() throws Exception {
        var approved = CompanyResponse.builder()
                .id(COMPANY_ID).name("Test Co").email("test@co.com")
                .status("APPROVED").build();
        when(companyService.approveCompany(COMPANY_ID)).thenReturn(approved);

        mockMvc.perform(patch("/api/platform/companies/" + COMPANY_ID + "/approve")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveCompany_companyAdmin_returns403() throws Exception {
        mockMvc.perform(patch("/api/platform/companies/" + COMPANY_ID + "/approve")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/platform/companies/{id}/reject

    @Test
    void rejectCompany_platformAdmin_returns200() throws Exception {
        var rejected = CompanyResponse.builder()
                .id(COMPANY_ID).name("Test Co").email("test@co.com")
                .status("REJECTED").rejectionReason("Invalid docs").build();
        when(companyService.rejectCompany(eq(COMPANY_ID), any())).thenReturn(rejected);

        mockMvc.perform(patch("/api/platform/companies/" + COMPANY_ID + "/reject")
                        .header("Authorization", platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Invalid docs"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // PATCH /api/platform/companies/{id}/suspend

    @Test
    void suspendCompany_platformAdmin_returns200() throws Exception {
        var suspended = CompanyResponse.builder()
                .id(COMPANY_ID).name("Test Co").email("test@co.com")
                .status("SUSPENDED").build();
        when(companyService.suspendCompany(COMPANY_ID)).thenReturn(suspended);

        mockMvc.perform(patch("/api/platform/companies/" + COMPANY_ID + "/suspend")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    // PATCH /api/platform/companies/{id}/reactivate

    @Test
    void reactivateCompany_platformAdmin_returns200() throws Exception {
        var active = CompanyResponse.builder()
                .id(COMPANY_ID).name("Test Co").email("test@co.com")
                .status("APPROVED").build();
        when(companyService.reactivateCompany(COMPANY_ID)).thenReturn(active);

        mockMvc.perform(patch("/api/platform/companies/" + COMPANY_ID + "/reactivate")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk());
    }
}
