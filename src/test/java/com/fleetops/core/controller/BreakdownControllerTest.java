package com.fleetops.core.controller;

import com.fleetops.core.module.breakdown.dto.BreakdownResponse;
import com.fleetops.core.module.breakdown.model.BreakdownStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BreakdownControllerTest extends BaseControllerIntegrationTest {

    private BreakdownResponse sampleBreakdown() {
        return BreakdownResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).vehiclePlateNumber("AAA-123AA")
                .fieldStaffId(12L).fieldStaffName("Field Staff")
                .latitude(6.5244).longitude(3.3792).description("Engine failure")
                .status(BreakdownStatus.REPORTED).reportedAt(LocalDateTime.now()).build();
    }

    // ── BreakdownController ────────────────────────────────────────────────

    // POST /api/breakdowns

    @Test
    void reportBreakdown_fieldStaff_returns201() throws Exception {
        when(breakdownService.report(any())).thenReturn(sampleBreakdown());

        mockMvc.perform(post("/api/breakdowns")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId":10,
                                  "latitude":6.5244,
                                  "longitude":3.3792,
                                  "description":"Engine failure"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Engine failure"));
    }

    @Test
    void reportBreakdown_fleetManager_returns403() throws Exception {
        mockMvc.perform(post("/api/breakdowns")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"latitude":6.5244,"longitude":3.3792,"description":"Engine failure"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportBreakdown_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/breakdowns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"latitude":6.5244,"longitude":3.3792,"description":"Engine failure"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportBreakdown_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/breakdowns")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10}
                                """))
                .andExpect(status().isBadRequest());
    }

    // GET /api/breakdowns

    @Test
    void getAllBreakdowns_fleetManager_returns200() throws Exception {
        when(breakdownService.getAll()).thenReturn(List.of(sampleBreakdown()));

        mockMvc.perform(get("/api/breakdowns")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllBreakdowns_companyAdmin_returns200() throws Exception {
        when(breakdownService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/breakdowns")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAllBreakdowns_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/breakdowns")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllBreakdowns_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/breakdowns"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/breakdowns/{id}

    @Test
    void getBreakdownById_fleetManager_returns200() throws Exception {
        when(breakdownService.getById(1L)).thenReturn(sampleBreakdown());

        mockMvc.perform(get("/api/breakdowns/1")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getBreakdownById_fieldStaff_returns200() throws Exception {
        when(breakdownService.getById(1L)).thenReturn(sampleBreakdown());

        mockMvc.perform(get("/api/breakdowns/1")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isOk());
    }

    @Test
    void getBreakdownById_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(get("/api/breakdowns/1")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/breakdowns/{id}/dispatch-replacement

    @Test
    void dispatchReplacement_fleetManager_returns200() throws Exception {
        var dispatched = BreakdownResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).vehiclePlateNumber("AAA-123AA")
                .fieldStaffId(12L).fieldStaffName("Field Staff")
                .latitude(6.5244).longitude(3.3792).description("Engine failure")
                .status(BreakdownStatus.REPLACEMENT_DISPATCHED)
                .replacementVehicleId(20L).replacementVehiclePlate("APP-456BB")
                .reportedAt(LocalDateTime.now()).build();
        when(breakdownService.dispatchReplacement(eq(1L), any())).thenReturn(dispatched);

        mockMvc.perform(patch("/api/breakdowns/1/dispatch-replacement")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"replacementVehicleId":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replacementVehicleId").value(20));
    }

    @Test
    void dispatchReplacement_fieldStaff_returns403() throws Exception {
        mockMvc.perform(patch("/api/breakdowns/1/dispatch-replacement")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"replacementVehicleId":20}
                                """))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/breakdowns/{id}/resolve

    @Test
    void resolveBreakdown_fleetManager_returns200() throws Exception {
        var resolved = BreakdownResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).vehiclePlateNumber("AAA-123AA")
                .fieldStaffId(12L).fieldStaffName("Field Staff")
                .latitude(6.5244).longitude(3.3792).description("Engine failure")
                .status(BreakdownStatus.RESOLVED).reportedAt(LocalDateTime.now()).build();
        when(breakdownService.resolve(1L)).thenReturn(resolved);

        mockMvc.perform(patch("/api/breakdowns/1/resolve")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void resolveBreakdown_fieldStaff_returns403() throws Exception {
        mockMvc.perform(patch("/api/breakdowns/1/resolve")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolveBreakdown_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/breakdowns/1/resolve"))
                .andExpect(status().isUnauthorized());
    }

    // ── PlatformBreakdownController ───────────────────────────────────────

    // GET /api/platform/breakdowns

    @Test
    void getAllBreakdownsPlatform_platformAdmin_returns200() throws Exception {
        when(breakdownService.getAllPlatform()).thenReturn(List.of(sampleBreakdown()));

        mockMvc.perform(get("/api/platform/breakdowns")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllBreakdownsPlatform_companyAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/breakdowns")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllBreakdownsPlatform_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/platform/breakdowns"))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/platform/breakdowns/{id}/dispatch-crew

    @Test
    void dispatchCrew_platformAdmin_returns200() throws Exception {
        var dispatched = BreakdownResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).vehiclePlateNumber("AAA-123AA")
                .fieldStaffId(12L).fieldStaffName("Field Staff")
                .latitude(6.5244).longitude(3.3792).description("Engine failure")
                .status(BreakdownStatus.CREW_DISPATCHED).assignedCrewId(13L).assignedCrewName("Maintenance Crew")
                .reportedAt(LocalDateTime.now()).build();
        when(breakdownService.dispatchCrew(eq(1L), any())).thenReturn(dispatched);

        mockMvc.perform(patch("/api/platform/breakdowns/1/dispatch-crew")
                        .header("Authorization", platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewId":13}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCrewId").value(13));
    }

    @Test
    void dispatchCrew_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/platform/breakdowns/1/dispatch-crew")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewId":13}
                                """))
                .andExpect(status().isForbidden());
    }
}
