package com.fleetops.core.mileage.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.mileage.dto.MileageLogRequest;
import com.fleetops.core.mileage.dto.MileageLogResponse;
import com.fleetops.core.mileage.entity.MileageLog;
import com.fleetops.core.mileage.repository.MileageLogRepository;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import com.fleetops.core.vehicle.service.VehicleLifecycleService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MileageLogServiceTest {

    @Mock private MileageLogRepository mileageLogRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private UserRepository userRepository;
    @Mock private TripRequestRepository tripRequestRepository;
    @Mock private MaintenanceEventProducer maintenanceEventProducer;
    @Mock private VehicleActivityProducer vehicleActivityProducer;
    @Mock private VehicleLifecycleService vehicleLifecycleService;

    @InjectMocks private MileageLogService mileageLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── submitLog ────────────────────────────────────────────────────────────

    @Test
    void submitLog_success_setsOdometerDirectlyAndSavesLog() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        Vehicle vehicle = vehicle(10L, 4000.0, 5000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(true);
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MileageLogResponse response = mileageLogService.submitLog(logRequest(10L, 4500.0));

        assertThat(response.getReportedMileage()).isEqualTo(4500.0);
        assertThat(vehicle.getCurrentMileage()).isEqualTo(4500.0);
        verify(mileageLogRepository).save(any(MileageLog.class));
        verify(maintenanceEventProducer, never()).publish(any());
    }

    @Test
    void submitLog_noCompletedTrip_throwsConflict() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        Vehicle vehicle = vehicle(10L, 4000.0, 5000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(false);

        assertThatThrownBy(() -> mileageLogService.submitLog(logRequest(10L, 4500.0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("completed trip");
        verify(mileageLogRepository, never()).save(any());
    }

    @Test
    void submitLog_milestoneReached_publishesMaintenanceEvent() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        User manager = manager(2L, "manager@fleetops.com");
        Vehicle vehicle = vehicle(10L, 4900.0, 5000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(true);
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER)).thenReturn(List.of(manager));

        mileageLogService.submitLog(logRequest(10L, 5100.0));

        ArgumentCaptor<MaintenanceFlagCreatedEvent> captor =
                ArgumentCaptor.forClass(MaintenanceFlagCreatedEvent.class);
        verify(maintenanceEventProducer).publish(captor.capture());
        MaintenanceFlagCreatedEvent event = captor.getValue();
        assertThat(event.getVehicleId()).isEqualTo(10L);
        assertThat(event.getMileageAtTrigger()).isEqualTo(5100.0);
        assertThat(event.getFleetManagerEmail()).isEqualTo("manager@fleetops.com");
    }

    @Test
    void submitLog_milestoneNotReached_doesNotPublish() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        Vehicle vehicle = vehicle(10L, 4000.0, 5000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(true);
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        mileageLogService.submitLog(logRequest(10L, 4100.0));

        verify(maintenanceEventProducer, never()).publish(any());
    }

    @Test
    void submitLog_milestoneReached_noActiveFleetManager_doesNotPublish() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        Vehicle vehicle = vehicle(10L, 4900.0, 5000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(true);
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER)).thenReturn(List.of());

        mileageLogService.submitLog(logRequest(10L, 5100.0));

        verify(maintenanceEventProducer, never()).publish(any());
    }

    @Test
    void submitLog_reportedMileageLowerThanCurrent_throwsConflict() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        Vehicle vehicle = vehicle(10L, 5000.0, 8000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(true);

        assertThatThrownBy(() -> mileageLogService.submitLog(logRequest(10L, 4800.0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("4800")
                .hasMessageContaining("5000");
        verify(mileageLogRepository, never()).save(any());
    }

    @Test
    void submitLog_reportedMileageEqualToCurrent_accepted() {
        mockSecurityContext("staff@fleetops.com");
        User staff = staff(1L, "staff@fleetops.com");
        Vehicle vehicle = vehicle(10L, 5000.0, 8000.0);

        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff));
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(1L, 10L, TripRequestStatus.COMPLETED))
                .thenReturn(true);
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mileageLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MileageLogResponse response = mileageLogService.submitLog(logRequest(10L, 5000.0));

        assertThat(response.getReportedMileage()).isEqualTo(5000.0);
        verify(mileageLogRepository).save(any());
    }

    @Test
    void submitLog_vehicleNotFound_throwsResourceNotFound() {
        mockSecurityContext("staff@fleetops.com");
        when(userRepository.findByEmail("staff@fleetops.com")).thenReturn(Optional.of(staff(1L, "staff@fleetops.com")));
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mileageLogService.submitLog(logRequest(99L, 100.0)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void submitLog_userNotFound_throwsResourceNotFound() {
        mockSecurityContext("ghost@fleetops.com");
        when(userRepository.findByEmail("ghost@fleetops.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mileageLogService.submitLog(logRequest(10L, 100.0)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getLogsByVehicle ─────────────────────────────────────────────────────

    @Test
    void getLogsByVehicle_success_returnsLogsOrderedByLatest() {
        Vehicle vehicle = vehicle(10L, 6000.0, 5000.0);
        User staff = staff(1L, "staff@fleetops.com");

        MileageLog log1 = MileageLog.builder()
                .id(1L).vehicle(vehicle).submittedBy(staff)
                .reportedMileage(6000.0)
                .loggedAt(LocalDateTime.now()).build();
        MileageLog log2 = MileageLog.builder()
                .id(2L).vehicle(vehicle).submittedBy(staff)
                .reportedMileage(5500.0)
                .loggedAt(LocalDateTime.now().minusDays(1)).build();

        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(mileageLogRepository.findByVehicleIdOrderByLoggedAtDesc(10L))
                .thenReturn(List.of(log1, log2));

        List<MileageLogResponse> result = mileageLogService.getLogsByVehicle(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReportedMileage()).isEqualTo(6000.0);
        assertThat(result.get(1).getReportedMileage()).isEqualTo(5500.0);
        assertThat(result.get(0).getSubmittedByName()).isEqualTo("Field Staff");
    }

    @Test
    void getLogsByVehicle_vehicleNotFound_throwsResourceNotFound() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mileageLogService.getLogsByVehicle(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getLogsByVehicle_noLogs_returnsEmptyList() {
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle(10L, 0.0, 5000.0)));
        when(mileageLogRepository.findByVehicleIdOrderByLoggedAtDesc(10L)).thenReturn(List.of());

        assertThat(mileageLogService.getLogsByVehicle(10L)).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private User staff(Long id, String email) {
        return User.builder().id(id).name("Field Staff").email(email)
                .role(UserRole.FIELD_STAFF).password("hashed").build();
    }

    private User manager(Long id, String email) {
        return User.builder().id(id).name("Fleet Manager").email(email)
                .role(UserRole.FLEET_MANAGER).password("hashed").build();
    }

    private Vehicle vehicle(Long id, Double currentMileage, Double milestoneInterval) {
        return Vehicle.builder().id(id).make("Toyota").model("Camry")
                .plateNumber("ABC-" + id).status(VehicleStatus.ASSIGNED)
                .currentMileage(currentMileage).milestoneInterval(milestoneInterval).build();
    }

    private MileageLogRequest logRequest(Long vehicleId, Double reportedMileage) {
        MileageLogRequest req = new MileageLogRequest();
        req.setVehicleId(vehicleId);
        req.setReportedMileage(reportedMileage);
        return req;
    }
}
