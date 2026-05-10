package com.fleetops.core.triprequest.service;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.exception.VehicleNotAvailableException;
import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.mileage.entity.MileageLog;
import com.fleetops.core.mileage.repository.MileageLogRepository;
import com.fleetops.core.triprequest.dto.CompleteTripRequest;
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
import org.springframework.security.access.AccessDeniedException;
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
    @Mock private MileageLogRepository mileageLogRepository;
    @Mock private MaintenanceEventProducer maintenanceEventProducer;
    @Mock private VehicleActivityProducer vehicleActivityProducer;

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
    void createRequest_success_notifiesFleetManagers() {
        mockSecurityContext("staff@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);
        User manager = fleetManager(5L, "manager@fleetops.com");
        TripRequest saved = pendingRequest(100L, staff, vehicle);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.PENDING))
                .thenReturn(false);
        when(vehicleAssignmentRepository.existsOverlappingAssignment(any(), any(), any())).thenReturn(false);
        when(tripRequestRepository.save(any())).thenReturn(saved);
        when(userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER)).thenReturn(List.of(manager));

        tripRequestService.createRequest(createDto(10L));

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("TRIP_REQUESTED");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("manager@fleetops.com");
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
        mockSecurityContext("manager@fleetops.com");
        User manager = fleetManager(2L, "manager@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = Vehicle.builder().id(10L).plateNumber("ABC-123")
                .status(VehicleStatus.ASSIGNED).currentMileage(0.0).milestoneInterval(5000.0).build();
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Port Harcourt")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TripRequestResponse response = tripRequestService.completeTrip(100L, null);

        assertThat(response.getStatus()).isEqualTo(TripRequestStatus.COMPLETED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository).save(vehicle);
        verify(mileageLogRepository, never()).save(any());
    }

    @Test
    void completeTrip_stillPending_throwsConflict() {
        User staff = fieldStaff(1L, "staff@fleetops.com");
        TripRequest pending = pendingRequest(100L, staff, availableVehicle(10L));
        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> tripRequestService.completeTrip(100L, null))
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

        assertThatThrownBy(() -> tripRequestService.completeTrip(100L, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completeTrip_notFound_throwsResourceNotFound() {
        when(tripRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripRequestService.completeTrip(999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completeTrip_fieldStaff_ownsTrip_success() {
        mockSecurityContext("staff@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = Vehicle.builder().id(10L).plateNumber("KJA-001AB")
                .status(VehicleStatus.ASSIGNED).currentMileage(1000.0).milestoneInterval(5000.0).build();
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Abuja")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TripRequestResponse response = tripRequestService.completeTrip(100L, null);

        assertThat(response.getStatus()).isEqualTo(TripRequestStatus.COMPLETED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void completeTrip_fieldStaff_notOwner_throwsAccessDenied() {
        mockSecurityContext("other@fleetops.com");
        User owner = fieldStaff(1L, "staff@fleetops.com");
        User other = fieldStaff(2L, "other@fleetops.com");
        Vehicle vehicle = assignedVehicle(10L);
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(owner).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Lagos")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("other@fleetops.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> tripRequestService.completeTrip(100L, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void completeTrip_withMileage_updatesVehicleAndCreatesLog() {
        mockSecurityContext("manager@fleetops.com");
        User manager = fleetManager(2L, "manager@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = Vehicle.builder().id(10L).plateNumber("KJA-001AB")
                .status(VehicleStatus.ASSIGNED).currentMileage(3000.0).milestoneInterval(5000.0).build();
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Kano")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        tripRequestService.completeTrip(100L, completionRequest(3500.0));

        assertThat(vehicle.getCurrentMileage()).isEqualTo(3500.0);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(mileageLogRepository).save(any(MileageLog.class));
        verify(maintenanceEventProducer, never()).publish(any());
    }

    @Test
    void completeTrip_withMilestoneReached_publishesMaintenanceEvent() {
        mockSecurityContext("manager@fleetops.com");
        User manager = fleetManager(2L, "manager@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = Vehicle.builder().id(10L).plateNumber("KJA-001AB")
                .status(VehicleStatus.ASSIGNED).currentMileage(4900.0).milestoneInterval(5000.0).build();
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Enugu")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER)).thenReturn(List.of(manager));

        tripRequestService.completeTrip(100L, completionRequest(5100.0));

        ArgumentCaptor<MaintenanceFlagCreatedEvent> captor =
                ArgumentCaptor.forClass(MaintenanceFlagCreatedEvent.class);
        verify(maintenanceEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getVehicleId()).isEqualTo(10L);
        assertThat(captor.getValue().getMileageAtTrigger()).isEqualTo(5100.0);
    }

    @Test
    void completeTrip_withMileageBelowCurrent_throwsConflict() {
        mockSecurityContext("manager@fleetops.com");
        User manager = fleetManager(2L, "manager@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = Vehicle.builder().id(10L).plateNumber("KJA-001AB")
                .status(VehicleStatus.ASSIGNED).currentMileage(5000.0).milestoneInterval(8000.0).build();
        TripRequest request = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Ibadan")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(tripRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(tripRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> tripRequestService.completeTrip(100L, completionRequest(4800.0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("4800")
                .hasMessageContaining("5000");
        verify(mileageLogRepository, never()).save(any());
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

    // ── getMyApprovedRequests ────────────────────────────────────────────────

    @Test
    void getMyApprovedRequests_returnsOnlyApprovedForCurrentStaff() {
        mockSecurityContext("staff@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        Vehicle vehicle = availableVehicle(10L);

        TripRequest approved = TripRequest.builder().id(101L).fieldStaff(staff).vehicle(vehicle)
                .status(TripRequestStatus.APPROVED).destination("Lagos")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(tripRequestRepository.findByFieldStaffIdAndStatus(1L, TripRequestStatus.APPROVED))
                .thenReturn(List.of(approved));

        List<TripRequestResponse> result = tripRequestService.getMyApprovedRequests();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(TripRequestStatus.APPROVED);
        assertThat(result.get(0).getFieldStaffId()).isEqualTo(1L);
    }

    @Test
    void getMyApprovedRequests_noApprovedTrips_returnsEmpty() {
        mockSecurityContext("staff@fleetops.com");
        User staff = fieldStaff(1L, "staff@fleetops.com");
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(tripRequestRepository.findByFieldStaffIdAndStatus(1L, TripRequestStatus.APPROVED))
                .thenReturn(List.of());

        assertThat(tripRequestService.getMyApprovedRequests()).isEmpty();
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

    private User fleetManager(Long id, String email) {
        return User.builder().id(id).name("Fleet Manager").email(email)
                .role(UserRole.FLEET_MANAGER).password("hashed").build();
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

    private Vehicle assignedVehicle(Long id) {
        return Vehicle.builder().id(id).make("Honda").model("Accord")
                .plateNumber("ABJ-" + id).status(VehicleStatus.ASSIGNED)
                .currentMileage(0.0).milestoneInterval(5000.0).build();
    }

    private CompleteTripRequest completionRequest(Double mileage) {
        CompleteTripRequest req = new CompleteTripRequest();
        req.setReportedMileage(mileage);
        return req;
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
