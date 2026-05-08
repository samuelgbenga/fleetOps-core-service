package com.fleetops.core.triprequest.service;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.exception.VehicleNotAvailableException;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.triprequest.dto.TripRequestCreate;
import com.fleetops.core.triprequest.dto.TripRequestResponse;
import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripRequestServiceTest {

    @Mock private TripRequestRepository tripRequestRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private VehicleAssignmentRepository vehicleAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationEventProducer notificationEventProducer;

    @InjectMocks private TripRequestService tripRequestService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── createRequest ────────────────────────────────────────────────────────

    @Test
    void createRequest_success_returnsPendingResponse() {
        mockSecurityContext("staff@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        TripRequest saved = pendingRequest(100L, staff, vehicle);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.PENDING))
                .thenReturn(false);
        when(vehicleAssignmentRepository.existsOverlappingAssignment(any(), any(), any())).thenReturn(false);
        when(tripRequestRepository.save(any())).thenReturn(saved);

        TripRequestResponse response = tripRequestService.createRequest(createDto(10L));

        assertThat(response.getStatus()).isEqualTo(TripRequestStatus.PENDING);
        assertThat(response.getVehicleId()).isEqualTo(10L);
        verify(tripRequestRepository).save(any());
    }

    @Test
    void createRequest_vehicleNotFound_throwsResourceNotFound() {
        mockSecurityContext("staff@fleetops.com");
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(fieldStaff(1L, "staff@fleetops.com")));
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripRequestService.createRequest(createDto(99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(tripRequestRepository, never()).save(any());
    }

    @Test
    void createRequest_vehicleAssigned_throwsVehicleNotAvailable() {
        mockSecurityContext("staff@fleetops.com");
        Vehicle assigned = Vehicle.builder().id(10L).plateNumber("ABC-123")
                .status(VehicleStatus.ASSIGNED).currentMileage(0.0).milestoneInterval(5000.0).build();

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(fieldStaff(1L, "staff@fleetops.com")));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(assigned));

        assertThatThrownBy(() -> tripRequestService.createRequest(createDto(10L)))
                .isInstanceOf(VehicleNotAvailableException.class)
                .hasMessageContaining("ABC-123");
    }

    @Test
    void createRequest_vehicleInMaintenance_throwsVehicleNotAvailable() {
        mockSecurityContext("staff@fleetops.com");
        Vehicle maintenance = Vehicle.builder().id(10L).plateNumber("ABC-123")
                .status(VehicleStatus.MAINTENANCE).currentMileage(0.0).milestoneInterval(5000.0).build();

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(fieldStaff(1L, "staff@fleetops.com")));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(maintenance));

        assertThatThrownBy(() -> tripRequestService.createRequest(createDto(10L)))
                .isInstanceOf(VehicleNotAvailableException.class);
    }

    @Test
    void createRequest_dateOverlap_throwsConflict() {
        mockSecurityContext("staff@fleetops.com");
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(fieldStaff(1L, "staff@fleetops.com")));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(availableVehicle(10L)));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.PENDING))
                .thenReturn(false);
        when(vehicleAssignmentRepository.existsOverlappingAssignment(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> tripRequestService.createRequest(createDto(10L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRequest_duplicatePendingForSameVehicle_throwsConflict() {
        mockSecurityContext("staff@fleetops.com");
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(fieldStaff(1L, "staff@fleetops.com")));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(availableVehicle(10L)));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> tripRequestService.createRequest(createDto(10L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pending request");
        verify(tripRequestRepository, never()).save(any());
    }

    // ── approveRequest ───────────────────────────────────────────────────────

    @Test
    void approveRequest_success_createsAssignmentAndNotifiesStaff() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        TripRequest request = pendingRequest(100L, staff, vehicle);

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(vehicleAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tripRequestRepository.findByVehicleIdAndStatus(10L, TripRequestStatus.PENDING))
                .thenReturn(List.of());

        TripRequestResponse response = tripRequestService.approveRequest(100L);

        assertThat(response.getStatus()).isEqualTo(TripRequestStatus.APPROVED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
        verify(vehicleAssignmentRepository).save(any(VehicleAssignment.class));

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("TRIP_APPROVED");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("staff@fleetops.com");
    }

    @Test
    void approveRequest_autoRejectsConflictingPendingRequests() {
        // Approved trip: June 1 – June 10
        // Competing pending A: starts June 8 → endDate(approved) June 10 isAfter June 8 → REJECTED
        // Competing pending B: starts June 11 → endDate(approved) June 10 NOT after June 11 → kept
        User staffA = fieldStaff(1L, "staff.a@fleetops.com");
        User staffB = fieldStaff(2L, "staff.b@fleetops.com");
        User staffC = fieldStaff(3L, "staff.c@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);

        LocalDate approvedStart = LocalDate.now().plusDays(1);
        LocalDate approvedEnd   = LocalDate.now().plusDays(10);

        TripRequest approved = TripRequest.builder().id(100L).fieldStaff(staffA).vehicle(vehicle)
                .status(TripRequestStatus.PENDING).destination("Lagos")
                .startDate(approvedStart).endDate(approvedEnd).build();

        TripRequest conflicting = TripRequest.builder().id(101L).fieldStaff(staffB).vehicle(vehicle)
                .status(TripRequestStatus.PENDING).destination("Abuja")
                .startDate(approvedEnd.minusDays(2)).endDate(approvedEnd.plusDays(3)).build();

        TripRequest nonConflicting = TripRequest.builder().id(102L).fieldStaff(staffC).vehicle(vehicle)
                .status(TripRequestStatus.PENDING).destination("Kano")
                .startDate(approvedEnd.plusDays(1)).endDate(approvedEnd.plusDays(5)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(approved));
        when(vehicleAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tripRequestRepository.findByVehicleIdAndStatus(10L, TripRequestStatus.PENDING))
                .thenReturn(List.of(conflicting, nonConflicting));

        tripRequestService.approveRequest(100L);

        // conflicting request should be REJECTED
        assertThat(conflicting.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
        // non-conflicting request should remain PENDING
        assertThat(nonConflicting.getStatus()).isEqualTo(TripRequestStatus.PENDING);

        // two notifications: TRIP_APPROVED (staffA) + TRIP_REJECTED (staffB only)
        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer, times(2)).publish(captor.capture());
        List<NotificationRequestEvent> events = captor.getAllValues();
        assertThat(events).extracting(NotificationRequestEvent::getType)
                .containsExactlyInAnyOrder("TRIP_APPROVED", "TRIP_REJECTED");
        assertThat(events).extracting(NotificationRequestEvent::getRecipientEmail)
                .containsExactlyInAnyOrder("staff.a@fleetops.com", "staff.b@fleetops.com");
    }

    @Test
    void approveRequest_notFound_throwsResourceNotFound() {
        when(tripRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripRequestService.approveRequest(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approveRequest_alreadyApproved_throwsConflict() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        TripRequest already = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Lagos")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> tripRequestService.approveRequest(100L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void approveRequest_rejected_throwsConflict() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        TripRequest rejected = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.REJECTED).destination("Abuja")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> tripRequestService.approveRequest(100L))
                .isInstanceOf(ConflictException.class);
    }

    // ── rejectRequest ────────────────────────────────────────────────────────

    @Test
    void rejectRequest_success_notifiesStaff() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        TripRequest request = pendingRequest(100L, staff, availableVehicle(10L));
        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TripRequestResponse response = tripRequestService.rejectRequest(100L);

        assertThat(response.getStatus()).isEqualTo(TripRequestStatus.REJECTED);

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("TRIP_REJECTED");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("staff@fleetops.com");
    }

    @Test
    void rejectRequest_notPending_throwsConflict() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        TripRequest already = TripRequest.builder().id(100L).fieldStaff(staff)
                .vehicle(availableVehicle(10L)).status(TripRequestStatus.APPROVED)
                .destination("Lagos").startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> tripRequestService.rejectRequest(100L))
                .isInstanceOf(ConflictException.class);
    }

    // ── completeTrip ─────────────────────────────────────────────────────────

    @Test
    void completeTrip_success_setsVehicleAvailableAndStatusCompleted() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = Vehicle.builder().id(10L).plateNumber("ABC-123")
                .status(VehicleStatus.ASSIGNED).currentMileage(0.0).milestoneInterval(5000.0).build();
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Port Harcourt")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TripRequestResponse response = tripRequestService.completeTrip(100L);

        assertThat(response.getStatus()).isEqualTo(TripRequestStatus.COMPLETED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void completeTrip_stillPending_throwsConflict() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        TripRequest pending = pendingRequest(100L, staff, availableVehicle(10L));
        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> tripRequestService.completeTrip(100L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void completeTrip_rejected_throwsConflict() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        TripRequest rejected = TripRequest.builder().id(100L).fieldStaff(staff)
                .vehicle(availableVehicle(10L)).status(TripRequestStatus.REJECTED)
                .destination("Lagos").startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> tripRequestService.completeTrip(100L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completeTrip_notFound_throwsResourceNotFound() {
        when(tripRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripRequestService.completeTrip(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getPendingRequests ───────────────────────────────────────────────────

    @Test
    void getPendingRequests_returnsOnlyPendingRequests() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        when(tripRequestRepository.findByStatus(TripRequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest(100L, staff, vehicle)));

        List<TripRequestResponse> result = tripRequestService.getPendingRequests();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(TripRequestStatus.PENDING);
    }

    // ── getAllRequests ────────────────────────────────────────────────────────

    @Test
    void getAllRequests_returnsAllStatuses() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        TripRequest approved = TripRequest.builder().id(101L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Ibadan")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(2)).build();

        when(tripRequestRepository.findAll())
                .thenReturn(List.of(pendingRequest(100L, staff, vehicle), approved));

        List<TripRequestResponse> result = tripRequestService.getAllRequests();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TripRequestResponse::getStatus)
                .containsExactlyInAnyOrder(TripRequestStatus.PENDING, TripRequestStatus.APPROVED);
    }

    // ── getMyRequests ────────────────────────────────────────────────────────

    @Test
    void getMyRequests_returnsCurrentUserRequests() {
        mockSecurityContext("staff@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(tripRequestRepository.findByFieldStaffId(1L))
                .thenReturn(List.of(pendingRequest(100L, staff, availableVehicle(10L))));

        List<TripRequestResponse> result = tripRequestService.getMyRequests();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldStaffId()).isEqualTo(1L);
    }

    @Test
    void getMyRequests_userNotFound_throwsResourceNotFound() {
        mockSecurityContext("nobody@fleetops.com");
        when(userRepository.findByEmail("nobody@fleetops.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripRequestService.getMyRequests())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private User fieldStaff(Long id, String email) {
        return User.builder().id(id).name("Field Staff").email(email)
                .role(UserRole.FIELD_STAFF).password("hashed").build();
    }

    private Vehicle availableVehicle(Long id) {
        return Vehicle.builder().id(id).make("Toyota").model("Camry")
                .plateNumber("ABC-" + id).status(VehicleStatus.AVAILABLE)
                .currentMileage(0.0).milestoneInterval(5000.0).build();
    }

    private TripRequest pendingRequest(Long id, User staff, Vehicle vehicle) {
        return TripRequest.builder().id(id).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.PENDING).destination("Lagos")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();
    }

    private TripRequestCreate createDto(Long vehicleId) {
        TripRequestCreate dto = new TripRequestCreate();
        dto.setVehicleId(vehicleId);
        dto.setDestination("Lagos");
        dto.setStartDate(LocalDate.now().plusDays(1));
        dto.setEndDate(LocalDate.now().plusDays(3));
        return dto;
    }
}
