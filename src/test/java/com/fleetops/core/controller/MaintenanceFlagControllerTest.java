package com.fleetops.core.controller;

import com.fleetops.core.module.maintenance.dto.MaintenanceFlagResponse;
import com.fleetops.core.module.maintenance.dto.MessageResponse;
import com.fleetops.core.module.maintenance.dto.QuotationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaintenanceFlagControllerTest extends BaseControllerIntegrationTest {

    private MaintenanceFlagResponse sampleFlag() {
        return MaintenanceFlagResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .triggerType("MANUAL").status("OPEN").description("Engine check needed")
                .openedAt(LocalDateTime.now()).build();
    }

    private QuotationResponse sampleQuotation() {
        return QuotationResponse.builder()
                .id(1L).flagId(1L).crewId(13L).crewName("Maintenance Crew")
                .estimatedCost(new BigDecimal("50000")).description("Parts and labour")
                .status("PENDING").revisionNumber(1).submittedAt(LocalDateTime.now()).build();
    }

    private MessageResponse sampleMessage() {
        return MessageResponse.builder()
                .id(1L).flagId(1L).senderId(11L).senderName("Fleet Manager")
                .senderRole("FLEET_MANAGER").message("Please expedite")
                .sentAt(LocalDateTime.now()).build();
    }

    // ── MaintenanceFlagController ──────────────────────────────────────────

    // POST /api/maintenance/flags

    @Test
    void createFlag_fleetManager_returns201() throws Exception {
        when(maintenanceFlagService.createFlag(any())).thenReturn(sampleFlag());

        mockMvc.perform(post("/api/maintenance/flags")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"description":"Engine check needed"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Engine check needed"));
    }

    @Test
    void createFlag_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(post("/api/maintenance/flags")
                        .header("Authorization", maintenanceCrewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"description":"Engine check"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFlag_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/maintenance/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"description":"Engine check"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/maintenance/flags

    @Test
    void getAllFlags_fleetManager_returns200() throws Exception {
        when(maintenanceFlagService.getAll()).thenReturn(List.of(sampleFlag()));

        mockMvc.perform(get("/api/maintenance/flags")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllFlags_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(get("/api/maintenance/flags")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllFlags_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/maintenance/flags"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/maintenance/flags/my

    @Test
    void getMyFlags_maintenanceCrew_returns200() throws Exception {
        when(maintenanceFlagService.getMyCrew()).thenReturn(List.of(sampleFlag()));

        mockMvc.perform(get("/api/maintenance/flags/my")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMyFlags_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/maintenance/flags/my")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/maintenance/flags/{id}

    @Test
    void getFlagById_fleetManager_returns200() throws Exception {
        when(maintenanceFlagService.getById(1L)).thenReturn(sampleFlag());

        mockMvc.perform(get("/api/maintenance/flags/1")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getFlagById_maintenanceCrew_returns200() throws Exception {
        when(maintenanceFlagService.getById(1L)).thenReturn(sampleFlag());

        mockMvc.perform(get("/api/maintenance/flags/1")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isOk());
    }

    @Test
    void getFlagById_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/maintenance/flags/1")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/maintenance/flags/vehicle/{vehicleId}

    @Test
    void getFlagsByVehicle_fleetManager_returns200() throws Exception {
        when(maintenanceFlagService.getByVehicle(10L)).thenReturn(List.of(sampleFlag()));

        mockMvc.perform(get("/api/maintenance/flags/vehicle/10")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getFlagsByVehicle_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(get("/api/maintenance/flags/vehicle/10")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isForbidden());
    }

    // POST /api/maintenance/flags/{id}/quotation

    @Test
    void submitQuotation_maintenanceCrew_returns201() throws Exception {
        when(maintenanceFlagService.submitQuotation(eq(1L), any())).thenReturn(sampleQuotation());

        mockMvc.perform(post("/api/maintenance/flags/1/quotation")
                        .header("Authorization", maintenanceCrewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estimatedCost":50000,"description":"Parts and labour"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedCost").value(50000));
    }

    @Test
    void submitQuotation_fleetManager_returns403() throws Exception {
        mockMvc.perform(post("/api/maintenance/flags/1/quotation")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estimatedCost":50000,"description":"Parts and labour"}
                                """))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/maintenance/flags/{id}/quotation/revise

    @Test
    void reviseQuotation_maintenanceCrew_returns200() throws Exception {
        when(maintenanceFlagService.reviseQuotation(eq(1L), any())).thenReturn(sampleQuotation());

        mockMvc.perform(patch("/api/maintenance/flags/1/quotation/revise")
                        .header("Authorization", maintenanceCrewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estimatedCost":45000,"description":"Revised estimate"}
                                """))
                .andExpect(status().isOk());
    }

    // PATCH /api/maintenance/flags/{id}/approve-quote

    @Test
    void approveQuotation_fleetManager_returns200() throws Exception {
        var approved = MaintenanceFlagResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .triggerType("MANUAL").status("IN_PROGRESS").description("Engine check needed")
                .openedAt(LocalDateTime.now()).build();
        when(maintenanceFlagService.approveQuotation(1L)).thenReturn(approved);

        mockMvc.perform(patch("/api/maintenance/flags/1/approve-quote")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void approveQuotation_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(patch("/api/maintenance/flags/1/approve-quote")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/maintenance/flags/{id}/reject-quote

    @Test
    void rejectQuotation_fleetManager_returns200() throws Exception {
        when(maintenanceFlagService.rejectQuotation(eq(1L), any())).thenReturn(sampleFlag());

        mockMvc.perform(patch("/api/maintenance/flags/1/reject-quote")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Quote too high"}
                                """))
                .andExpect(status().isOk());
    }

    // PATCH /api/maintenance/flags/{id}/progress

    @Test
    void updateProgress_maintenanceCrew_returns200() throws Exception {
        when(maintenanceFlagService.updateProgress(eq(1L), any())).thenReturn(sampleFlag());

        mockMvc.perform(patch("/api/maintenance/flags/1/progress")
                        .header("Authorization", maintenanceCrewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progressNotes":"Replaced engine oil"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateProgress_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/maintenance/flags/1/progress")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progressNotes":"test"}
                                """))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/maintenance/flags/{id}/done

    @Test
    void markDone_maintenanceCrew_returns200() throws Exception {
        var done = MaintenanceFlagResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .triggerType("MANUAL").status("PENDING_APPROVAL").description("Engine check done")
                .openedAt(LocalDateTime.now()).build();
        when(maintenanceFlagService.markDone(1L)).thenReturn(done);

        mockMvc.perform(patch("/api/maintenance/flags/1/done")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void markDone_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/maintenance/flags/1/done")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/maintenance/flags/{id}/resolve

    @Test
    void resolveFlag_fleetManager_returns200() throws Exception {
        var resolved = MaintenanceFlagResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .triggerType("MANUAL").status("RESOLVED").description("Engine check done")
                .openedAt(LocalDateTime.now()).resolvedAt(LocalDateTime.now()).build();
        when(maintenanceFlagService.resolve(eq(1L), any())).thenReturn(resolved);

        mockMvc.perform(patch("/api/maintenance/flags/1/resolve")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stars":5,"comment":"Great work"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void resolveFlag_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(patch("/api/maintenance/flags/1/resolve")
                        .header("Authorization", maintenanceCrewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stars":5}
                                """))
                .andExpect(status().isForbidden());
    }

    // POST /api/maintenance/flags/{id}/ratings

    @Test
    void rateFlag_fleetManager_returns200() throws Exception {
        when(maintenanceFlagService.resolve(eq(1L), any())).thenReturn(sampleFlag());

        mockMvc.perform(post("/api/maintenance/flags/1/ratings")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stars":4,"comment":"Good job"}
                                """))
                .andExpect(status().isOk());
    }

    // POST /api/maintenance/flags/{id}/messages

    @Test
    void sendMessage_fleetManager_returns201() throws Exception {
        when(maintenanceFlagService.sendMessage(eq(1L), any())).thenReturn(sampleMessage());

        mockMvc.perform(post("/api/maintenance/flags/1/messages")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Please expedite"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Please expedite"));
    }

    @Test
    void sendMessage_maintenanceCrew_returns201() throws Exception {
        when(maintenanceFlagService.sendMessage(eq(1L), any())).thenReturn(sampleMessage());

        mockMvc.perform(post("/api/maintenance/flags/1/messages")
                        .header("Authorization", maintenanceCrewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Parts ordered"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void sendMessage_fieldStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/maintenance/flags/1/messages")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Test"}
                                """))
                .andExpect(status().isForbidden());
    }

    // GET /api/maintenance/flags/{id}/messages

    @Test
    void getMessages_fleetManager_returns200() throws Exception {
        when(maintenanceFlagService.getMessages(1L)).thenReturn(List.of(sampleMessage()));

        mockMvc.perform(get("/api/maintenance/flags/1/messages")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Please expedite"));
    }

    @Test
    void getMessages_maintenanceCrew_returns200() throws Exception {
        when(maintenanceFlagService.getMessages(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/maintenance/flags/1/messages")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMessages_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/maintenance/flags/1/messages")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    // ── PlatformMaintenanceFlagController ──────────────────────────────────

    // GET /api/platform/maintenance/flags

    @Test
    void getAllFlagsPlatform_platformAdmin_returns200() throws Exception {
        when(maintenanceFlagService.getAllPlatform()).thenReturn(List.of(sampleFlag()));

        mockMvc.perform(get("/api/platform/maintenance/flags")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllFlagsPlatform_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/maintenance/flags")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllFlagsPlatform_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/platform/maintenance/flags"))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/platform/maintenance/flags/{id}/assign-crew

    @Test
    void assignCrew_platformAdmin_returns200() throws Exception {
        var assigned = MaintenanceFlagResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .triggerType("MANUAL").status("ASSIGNED").description("Engine check needed")
                .assignedCrewId(13L).assignedCrewName("Maintenance Crew")
                .openedAt(LocalDateTime.now()).build();
        when(maintenanceFlagService.assignCrew(eq(1L), any())).thenReturn(assigned);

        mockMvc.perform(patch("/api/platform/maintenance/flags/1/assign-crew")
                        .header("Authorization", platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewId":13}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCrewId").value(13));
    }

    @Test
    void assignCrew_companyAdmin_returns403() throws Exception {
        mockMvc.perform(patch("/api/platform/maintenance/flags/1/assign-crew")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewId":13}
                                """))
                .andExpect(status().isForbidden());
    }
}
