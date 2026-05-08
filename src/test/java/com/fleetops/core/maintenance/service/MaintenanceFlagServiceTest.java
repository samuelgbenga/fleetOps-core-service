package com.fleetops.core.maintenance.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.maintenance.dto.AssignFlagRequest;
import com.fleetops.core.maintenance.dto.MaintenanceFlagResponse;
import com.fleetops.core.maintenance.dto.ProgressUpdateRequest;
import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceFlagServiceTest {

    @Mock private MaintenanceFlagRepository maintenanceFlagRepository;
    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private NotificationEventProducer notificationEventProducer;

    @InjectMocks private MaintenanceFlagService maintenanceFlagService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── getAllFlags ───────────────────────────────────────────────────────────

    @Test
    void getAllFlags_returnsAllFlags() {
        Vehicle vehicle = vehicle(10L);
        when(maintenanceFlagRepository.findAll()).thenReturn(List.of(
                openFlag(1L, vehicle),
                flagWithStatus(2L, vehicle, FlagStatus.RESOLVED)
        ));

        List<MaintenanceFlagResponse> result = maintenanceFlagService.getAllFlags();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MaintenanceFlagResponse::getStatus)
                .containsExactlyInAnyOrder(FlagStatus.OPEN, FlagStatus.RESOLVED);
    }

    @Test
    void getAllFlags_empty_returnsEmptyList() {
        when(maintenanceFlagRepository.findAll()).thenReturn(List.of());
        assertThat(maintenanceFlagService.getAllFlags()).isEmpty();
    }

    // ── getMyAssignedFlags ───────────────────────────────────────────────────

    @Test
    void getMyAssignedFlags_success_returnsAssignedToCurrentUser() {
        mockSecurityContext("tech@fleetops.com");
        User tech = maintenanceUser(5L, "tech@fleetops.com");
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = openFlag(1L, vehicle);
        flag.setAssignedTo(tech);
        flag.setStatus(FlagStatus.ASSIGNED);

        when(userRepository.findByEmail("tech@fleetops.com")).thenReturn(Optional.of(tech));
        when(maintenanceFlagRepository.findByAssignedToId(5L)).thenReturn(List.of(flag));

        List<MaintenanceFlagResponse> result = maintenanceFlagService.getMyAssignedFlags();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAssignedToId()).isEqualTo(5L);
    }

    @Test
    void getMyAssignedFlags_userNotFound_throwsResourceNotFound() {
        mockSecurityContext("nobody@fleetops.com");
        when(userRepository.findByEmail("nobody@fleetops.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceFlagService.getMyAssignedFlags())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── assignFlag ───────────────────────────────────────────────────────────

    @Test
    void assignFlag_success_setsAssignedStatusAndNotifiesTech() {
        mockSecurityContext("manager@fleetops.com");
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = openFlag(1L, vehicle);
        User tech = maintenanceUser(5L, "tech@fleetops.com");
        User manager = manager(2L, "manager@fleetops.com");

        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(tech));
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(maintenanceFlagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AssignFlagRequest req = new AssignFlagRequest();
        req.setMaintenanceTeamUserId(5L);

        MaintenanceFlagResponse response = maintenanceFlagService.assignFlag(1L, req);

        assertThat(response.getStatus()).isEqualTo(FlagStatus.ASSIGNED);

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("FLAG_ASSIGNED");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("tech@fleetops.com");
    }

    @Test
    void assignFlag_flagNotOpen_throwsConflict() {
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.ASSIGNED);
        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));

        AssignFlagRequest req = new AssignFlagRequest();
        req.setMaintenanceTeamUserId(5L);

        assertThatThrownBy(() -> maintenanceFlagService.assignFlag(1L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void assignFlag_maintenanceUserNotFound_throwsResourceNotFound() {
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = openFlag(1L, vehicle);

        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        AssignFlagRequest req = new AssignFlagRequest();
        req.setMaintenanceTeamUserId(99L);

        assertThatThrownBy(() -> maintenanceFlagService.assignFlag(1L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void assignFlag_flagNotFound_throwsResourceNotFound() {
        when(maintenanceFlagRepository.findById(999L)).thenReturn(Optional.empty());

        AssignFlagRequest req = new AssignFlagRequest();
        req.setMaintenanceTeamUserId(5L);

        assertThatThrownBy(() -> maintenanceFlagService.assignFlag(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateProgress ───────────────────────────────────────────────────────

    @Test
    void updateProgress_fromAssigned_setsInProgressAndNotifiesManager() {
        Vehicle vehicle = vehicle(10L);
        User manager = manager(2L, "manager@fleetops.com");
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.ASSIGNED);
        flag.setAssignedBy(manager);

        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(maintenanceFlagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProgressUpdateRequest req = new ProgressUpdateRequest();
        req.setProgressNotes("Engine oil replaced");

        MaintenanceFlagResponse response = maintenanceFlagService.updateProgress(1L, req);

        assertThat(response.getStatus()).isEqualTo(FlagStatus.IN_PROGRESS);
        assertThat(response.getProgressNotes()).isEqualTo("Engine oil replaced");

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("FLAG_PROGRESS");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("manager@fleetops.com");
    }

    @Test
    void updateProgress_fromInProgress_updatesNotes() {
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.IN_PROGRESS);
        flag.setAssignedBy(null);

        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(maintenanceFlagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProgressUpdateRequest req = new ProgressUpdateRequest();
        req.setProgressNotes("Filters also replaced");

        MaintenanceFlagResponse response = maintenanceFlagService.updateProgress(1L, req);

        assertThat(response.getProgressNotes()).isEqualTo("Filters also replaced");
        verify(notificationEventProducer, never()).publish(any());
    }

    @Test
    void updateProgress_flagIsOpen_throwsConflict() {
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = openFlag(1L, vehicle);
        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));

        ProgressUpdateRequest req = new ProgressUpdateRequest();
        req.setProgressNotes("notes");

        assertThatThrownBy(() -> maintenanceFlagService.updateProgress(1L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ASSIGNED or IN_PROGRESS");
    }

    @Test
    void updateProgress_flagIsResolved_throwsConflict() {
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.RESOLVED);
        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));

        ProgressUpdateRequest req = new ProgressUpdateRequest();
        req.setProgressNotes("notes");

        assertThatThrownBy(() -> maintenanceFlagService.updateProgress(1L, req))
                .isInstanceOf(ConflictException.class);
    }

    // ── resolveFlag ───────────────────────────────────────────────────────────

    @Test
    void resolveFlag_success_setsResolvedAndVehicleAvailable() {
        Vehicle vehicle = vehicle(10L);
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        User manager = manager(2L, "manager@fleetops.com");
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.IN_PROGRESS);
        flag.setAssignedBy(manager);

        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(maintenanceFlagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MaintenanceFlagResponse response = maintenanceFlagService.resolveFlag(1L);

        assertThat(response.getStatus()).isEqualTo(FlagStatus.RESOLVED);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository).save(vehicle);

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("FLAG_RESOLVED");
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("manager@fleetops.com");
    }

    @Test
    void resolveFlag_noAssignedBy_vehicleStillBecomesAvailable_noNotification() {
        Vehicle vehicle = vehicle(10L);
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.ASSIGNED);
        flag.setAssignedBy(null);

        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(maintenanceFlagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        maintenanceFlagService.resolveFlag(1L);

        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(notificationEventProducer, never()).publish(any());
    }

    @Test
    void resolveFlag_alreadyResolved_throwsConflict() {
        Vehicle vehicle = vehicle(10L);
        MaintenanceFlag flag = flagWithStatus(1L, vehicle, FlagStatus.RESOLVED);
        when(maintenanceFlagRepository.findById(1L)).thenReturn(Optional.of(flag));

        assertThatThrownBy(() -> maintenanceFlagService.resolveFlag(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    void resolveFlag_notFound_throwsResourceNotFound() {
        when(maintenanceFlagRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceFlagService.resolveFlag(999L))
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

    private Vehicle vehicle(Long id) {
        return Vehicle.builder().id(id).make("Toyota").model("Hilux")
                .plateNumber("VEH-" + id).status(VehicleStatus.MAINTENANCE)
                .currentMileage(10000.0).milestoneInterval(5000.0).build();
    }

    private User maintenanceUser(Long id, String email) {
        return User.builder().id(id).name("Tech User").email(email)
                .role(UserRole.MAINTENANCE_TEAM).password("hashed").build();
    }

    private User manager(Long id, String email) {
        return User.builder().id(id).name("Manager").email(email)
                .role(UserRole.FLEET_MANAGER).password("hashed").build();
    }

    private MaintenanceFlag openFlag(Long id, Vehicle vehicle) {
        return MaintenanceFlag.builder()
                .id(id).vehicle(vehicle).mileageAtTrigger(5000.0)
                .status(FlagStatus.OPEN).build();
    }

    private MaintenanceFlag flagWithStatus(Long id, Vehicle vehicle, FlagStatus status) {
        return MaintenanceFlag.builder()
                .id(id).vehicle(vehicle).mileageAtTrigger(5000.0)
                .status(status).build();
    }
}
