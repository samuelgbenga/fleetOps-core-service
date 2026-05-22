package com.fleetops.core.controller;

import com.fleetops.core.module.mileage.dto.MileageLogResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MileageLogControllerTest extends BaseControllerIntegrationTest {

    private MileageLogResponse sampleLog() {
        return MileageLogResponse.builder()
                .id(1L).vehicleId(10L).plateNumber("AAA-123AA")
                .submittedById(12L).submittedByName("Field Staff")
                .reportedMileage(15000.0).loggedAt(LocalDateTime.now()).build();
    }

    // POST /api/mileage-logs

    @Test
    void submitMileageLog_fieldStaff_returns201() throws Exception {
        when(mileageLogService.submit(any())).thenReturn(sampleLog());

        mockMvc.perform(post("/api/mileage-logs")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"reportedMileage":15000.0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedMileage").value(15000.0));
    }

    @Test
    void submitMileageLog_fleetManager_returns403() throws Exception {
        mockMvc.perform(post("/api/mileage-logs")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"reportedMileage":15000.0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitMileageLog_companyAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/mileage-logs")
                        .header("Authorization", companyAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"reportedMileage":15000.0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitMileageLog_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/mileage-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"reportedMileage":15000.0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitMileageLog_negativeMileage_returns400() throws Exception {
        mockMvc.perform(post("/api/mileage-logs")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"reportedMileage":-500.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitMileageLog_missingVehicleId_returns400() throws Exception {
        mockMvc.perform(post("/api/mileage-logs")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedMileage":15000.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    // GET /api/mileage-logs/vehicle/{vehicleId}

    @Test
    void getMileageLogsByVehicle_fleetManager_returns200() throws Exception {
        when(mileageLogService.getByVehicle(10L)).thenReturn(List.of(sampleLog()));

        mockMvc.perform(get("/api/mileage-logs/vehicle/10")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value(10));
    }

    @Test
    void getMileageLogsByVehicle_companyAdmin_returns200() throws Exception {
        when(mileageLogService.getByVehicle(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/mileage-logs/vehicle/10")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMileageLogsByVehicle_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/mileage-logs/vehicle/10")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMileageLogsByVehicle_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/mileage-logs/vehicle/10"))
                .andExpect(status().isUnauthorized());
    }
}
