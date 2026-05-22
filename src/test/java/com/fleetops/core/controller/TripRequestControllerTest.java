package com.fleetops.core.controller;

import com.fleetops.core.module.triprequest.dto.TripRequestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TripRequestControllerTest extends BaseControllerIntegrationTest {

    private TripRequestResponse sampleTrip() {
        return TripRequestResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .requestedById(12L).requestedByName("Field Staff")
                .destination("Lagos").startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .status("PENDING").createdAt(LocalDateTime.now()).build();
    }

    // POST /api/trip-requests

    @Test
    void createTrip_fieldStaff_returns201() throws Exception {
        when(tripRequestService.create(any())).thenReturn(sampleTrip());

        mockMvc.perform(post("/api/trip-requests")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId":10,
                                  "destination":"Lagos",
                                  "startDate":"%s",
                                  "endDate":"%s"
                                }
                                """.formatted(
                                LocalDate.now().plusDays(1),
                                LocalDate.now().plusDays(3)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destination").value("Lagos"));
    }

    @Test
    void createTrip_fleetManager_returns403() throws Exception {
        mockMvc.perform(post("/api/trip-requests")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"destination":"Lagos",
                                 "startDate":"%s","endDate":"%s"}
                                """.formatted(
                                LocalDate.now().plusDays(1),
                                LocalDate.now().plusDays(3)
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTrip_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/trip-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":10,"destination":"Lagos",
                                 "startDate":"%s","endDate":"%s"}
                                """.formatted(
                                LocalDate.now().plusDays(1),
                                LocalDate.now().plusDays(3)
                        )))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/trip-requests

    @Test
    void getAllTrips_fleetManager_returns200() throws Exception {
        when(tripRequestService.getAll()).thenReturn(List.of(sampleTrip()));

        mockMvc.perform(get("/api/trip-requests")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllTrips_companyAdmin_returns200() throws Exception {
        when(tripRequestService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/trip-requests")
                        .header("Authorization", companyAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAllTrips_fieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/trip-requests")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllTrips_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/trip-requests"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/trip-requests/all

    @Test
    void getAllTripsAlias_fleetManager_returns200() throws Exception {
        when(tripRequestService.getAll()).thenReturn(List.of(sampleTrip()));

        mockMvc.perform(get("/api/trip-requests/all")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk());
    }

    // GET /api/trip-requests/my

    @Test
    void getMyTrips_fieldStaff_returns200() throws Exception {
        when(tripRequestService.getMy()).thenReturn(List.of(sampleTrip()));

        mockMvc.perform(get("/api/trip-requests/my")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getMyTrips_fleetManager_returns403() throws Exception {
        mockMvc.perform(get("/api/trip-requests/my")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    // GET /api/trip-requests/my/approved

    @Test
    void getMyApprovedTrips_fieldStaff_returns200() throws Exception {
        var approved = TripRequestResponse.builder()
                .id(2L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .requestedById(12L).destination("Abuja").status("APPROVED").build();
        when(tripRequestService.getMyApproved()).thenReturn(List.of(approved));

        mockMvc.perform(get("/api/trip-requests/my/approved")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void getMyApprovedTrips_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/trip-requests/my/approved"))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/trip-requests/{id}/approve

    @Test
    void approveTrip_fleetManager_returns200() throws Exception {
        var approved = TripRequestResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .requestedById(12L).destination("Lagos").status("APPROVED").build();
        when(tripRequestService.approve(1L)).thenReturn(approved);

        mockMvc.perform(patch("/api/trip-requests/1/approve")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveTrip_fieldStaff_returns403() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/approve")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveTrip_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/trip-requests/{id}/reject

    @Test
    void rejectTrip_fleetManager_returns200() throws Exception {
        var rejected = TripRequestResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .requestedById(12L).destination("Lagos").status("REJECTED")
                .rejectionReason("Vehicle not available").build();
        when(tripRequestService.reject(eq(1L), any())).thenReturn(rejected);

        mockMvc.perform(patch("/api/trip-requests/1/reject")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Vehicle not available"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejectTrip_missingReason_returns400() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/reject")
                        .header("Authorization", fleetManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectTrip_fieldStaff_returns403() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/reject")
                        .header("Authorization", fieldStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Not allowed"}
                                """))
                .andExpect(status().isForbidden());
    }

    // PATCH /api/trip-requests/{id}/complete

    @Test
    void completeTrip_fleetManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/complete")
                        .header("Authorization", fleetManagerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeTrip_fieldStaff_returns200() throws Exception {
        var completed = TripRequestResponse.builder()
                .id(1L).companyId(COMPANY_ID).vehicleId(10L).plateNumber("AAA-123AA")
                .requestedById(12L).destination("Lagos").status("COMPLETED").build();
        when(tripRequestService.complete(1L)).thenReturn(completed);

        mockMvc.perform(patch("/api/trip-requests/1/complete")
                        .header("Authorization", fieldStaffToken))
                .andExpect(status().isOk());
    }

    @Test
    void completeTrip_maintenanceCrew_returns403() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/complete")
                        .header("Authorization", maintenanceCrewToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeTrip_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/trip-requests/1/complete"))
                .andExpect(status().isUnauthorized());
    }
}
