package com.fleetops.core.controller;

import com.fleetops.core.module.vehicle.dto.VehicleImageResponse;
import com.fleetops.core.module.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VehicleControllerTest extends BaseControllerIntegrationTest {

    private VehicleResponse sampleVehicle() {
        return VehicleResponse.builder()
                .id(1L).companyId(COMPANY_ID).make("Toyota").model("Hilux")
                .plateNumber("AAA-123AA").status("AVAILABLE").build();
    }

    // ── VehicleController ──────────────────────────────────────────────────

    // POST /api/vehicles

    @Test
    void registerVehicle_fleetManager_returns201() throws Exception {
        when(vehicleService.registerVehicle(any())).thenReturn(sampleVehicle());

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "make":"Toyota","model":"Hilux",
                                  "plateNumber":"AAA-123AA"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.make").value("Toyota"));
    }

    @Test
    void registerVehicle_companyAdmin_returns201() throws Exception {
        when(vehicleService.registerVehicle(any())).thenReturn(sampleVehicle());

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "make":"Toyota","model":"Hilux",
                                  "plateNumber":"AAA-123AA"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void registerVehicle_fieldStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"make":"Toyota","model":"Hilux","plateNumber":"AAA-123AA"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerVehicle_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"make":"Toyota","model":"Hilux","plateNumber":"AAA-123AA"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/vehicles

    @Test
    void getAllVehicles_fleetManager_returns200() throws Exception {
        when(vehicleService.getAllVehicles()).thenReturn(List.of(sampleVehicle()));

        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].make").value("Toyota"));
    }

    @Test
    void getAllVehicles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllVehicles_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/vehicles/available

    @Test
    void getAvailableVehicles_fieldStaff_returns200() throws Exception {
        when(vehicleService.getAvailableVehicles()).thenReturn(List.of(sampleVehicle()));

        mockMvc.perform(get("/api/vehicles/available")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAvailableVehicles_fleetManager_returns200() throws Exception {
        when(vehicleService.getAvailableVehicles()).thenReturn(List.of());

        mockMvc.perform(get("/api/vehicles/available")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAvailableVehicles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/vehicles/available"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/vehicles/{id}

    @Test
    void getVehicleById_fleetManager_returns200() throws Exception {
        when(vehicleService.getVehicleById(1L)).thenReturn(sampleVehicle());

        mockMvc.perform(get("/api/vehicles/1")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getVehicleById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/vehicles/1"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/vehicles/{id}/health

    @Test
    void getVehicleHealth_fleetManager_returns200() throws Exception {
        when(vehicleService.getVehicleById(1L)).thenReturn(sampleVehicle());

        mockMvc.perform(get("/api/vehicles/1/health")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getVehicleHealth_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/vehicles/1/health")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/vehicles/{id}/milestone-interval

    @Test
    void updateMilestoneInterval_fleetManager_returns200() throws Exception {
        when(vehicleService.updateMilestoneInterval(eq(1L), any())).thenReturn(sampleVehicle());

        mockMvc.perform(patch("/api/vehicles/1/milestone-interval")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"milestoneInterval":5000.0}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateMilestoneInterval_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/vehicles/1/milestone-interval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"milestoneInterval":5000.0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // POST /api/vehicles/{id}/media

    @Test
    void uploadVehicleMedia_fleetManager_returns201() throws Exception {
        var imageResponse = VehicleImageResponse.builder()
                .id(1L).imageUrl("https://example.com/img.jpg").imageId("pub123").build();
        when(vehicleService.uploadVehicleImage(eq(1L), any())).thenReturn(imageResponse);

        mockMvc.perform(post("/api/vehicles/1/media")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageUrl":"https://example.com/img.jpg","imageId":"pub123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/img.jpg"));
    }

    @Test
    void uploadVehicleMedia_fieldStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/vehicles/1/media")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageUrl":"https://example.com/img.jpg","imageId":"pub123"}
                                """))
                .andExpect(status().isForbidden());
    }

    // DELETE /api/vehicles/{id}/media/{mediaId}

    @Test
    void deleteVehicleMedia_fleetManager_returns204() throws Exception {
        mockMvc.perform(delete("/api/vehicles/1/media/pub123")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteVehicleMedia_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/vehicles/1/media/pub123"))
                .andExpect(status().isUnauthorized());
    }

    // ── VehicleLifecycleController ─────────────────────────────────────────

    // GET /api/vehicles/{vehicleId}/lifecycle.pdf

    @Test
    void downloadLifecyclePdf_fleetManager_returns200() throws Exception {
        when(vehicleLifecyclePdfService.generate(1L)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/vehicles/1/lifecycle.pdf")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void downloadLifecyclePdf_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/vehicles/1/lifecycle.pdf")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadLifecyclePdf_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/vehicles/1/lifecycle.pdf"))
                .andExpect(status().isUnauthorized());
    }
}