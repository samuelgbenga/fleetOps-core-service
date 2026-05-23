package com.fleetops.core.module.breakdown.service;

import com.fleetops.core.module.breakdown.dto.BreakdownReportRequest;
import com.fleetops.core.module.breakdown.dto.DispatchCrewRequest;
import com.fleetops.core.module.breakdown.dto.DispatchReplacementRequest;
import com.fleetops.core.module.breakdown.model.BreakdownReport;
import com.fleetops.core.module.breakdown.model.BreakdownStatus;
import com.fleetops.core.module.breakdown.repository.BreakdownRepository;
import com.fleetops.core.module.breakdown.service.impl.BreakdownServiceImpl;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleService;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.BreakdownException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BreakdownServiceImplTest {

    @Mock BreakdownRepository breakdownRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock TripRequestRepository tripRequestRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock MaintenanceFlagRepository maintenanceFlagRepository;
    @Mock VehicleActivityProducer vehicleActivityProducer;
    @Mock NotificationEventProducer notificationEventProducer;
    @Mock VehicleService vehicleService;

    @InjectMocks BreakdownServiceImpl breakdownService;

    private static final Long COMPANY_ID = 10L;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, USER_ID, "FLEET_MANAGER", "COMPANY");
        ReflectionTestUtils.setField(breakdownService, "breakdownTopic", "breakdown-events");
        // approved-trip check passes by default for report tests; lenient to avoid UnnecessaryStubbingException
        // in tests that throw before reaching this check (e.g. company/vehicle/user not found)
        lenient().when(tripRequestRepository.findByRequestedByIdAndVehicleIdAndStatus(
                any(), any(), eq(TripRequestStatus.APPROVED)))
                .thenReturn(Optional.of(mock(TripRequest.class)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Company buildCompany() {
        return Company.builder().id(COMPANY_ID).name("Fleet Co").build();
    }

    private Vehicle buildVehicle(Long id, VehicleStatus status) {
        return Vehicle.builder().id(id).company(buildCompany()).plateNumber("ABC-123-XY")
                .currentMileage(0.0).milestoneInterval(10000.0)
                .maxMileage(300000.0).maxTrips(500).maxMaintenanceRounds(30)
                .purchasePrice(new BigDecimal("5000000"))
                .purchaseDate(LocalDate.now().minusYears(2))
                .totalMaintenanceSpend(BigDecimal.ZERO).breakdownCount(0)
                .status(status).lifecyclePercentage(0.0).markedForSale(false)
                .make("Toyota").model("Camry").build();
    }

    private User buildUser(Long id, Role role) {
        return User.builder().id(id).name("User").email("u@test.com")
                .password("enc").role(role).userType(UserType.COMPANY)
                .company(buildCompany()).active(true).available(true).build();
    }

    private User buildCrewUser(Long id) {
        return User.builder().id(id).name("Crew").email("crew@test.com")
                .password("enc").role(Role.MAINTENANCE_CREW).userType(UserType.PLATFORM)
                .active(true).available(true).build();
    }

    private BreakdownReport buildReport(Long id, BreakdownStatus status, Vehicle vehicle, User fieldStaff) {
        return BreakdownReport.builder()
                .id(id).company(buildCompany()).vehicle(vehicle).fieldStaff(fieldStaff)
                .latitude(6.5244).longitude(3.3792).locationDescription("Lagos Island")
                .description("Engine failure").status(status).build();
    }

    // ─── report ───────────────────────────────────────────────────────────────

    @Test
    void report_returnsResponse() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setLocationDescription("Lagos Island"); req.setDescription("Engine failure");

        assertThat(breakdownService.report(req)).isNotNull();
    }

    @Test
    void report_vehicleSetBrokenDown() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BROKEN_DOWN);
    }

    @Test
    void report_statusIsReported() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        ArgumentCaptor<BreakdownReport> captor = ArgumentCaptor.forClass(BreakdownReport.class);
        verify(breakdownRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BreakdownStatus.REPORTED);
    }

    @Test
    void report_companyNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());
        var req = new BreakdownReportRequest();
        req.setVehicleId(1L);
        assertThatThrownBy(() -> breakdownService.report(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void report_vehicleNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.empty());
        var req = new BreakdownReportRequest();
        req.setVehicleId(1L);
        assertThatThrownBy(() -> breakdownService.report(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void report_callsRecomputeHealth() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        verify(vehicleService).recomputeHealthForVehicle(vehicle);
    }

    @Test
    void report_notifiesFleetManagers() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User manager = buildUser(20L, Role.FLEET_MANAGER);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of(manager));
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        verify(notificationEventProducer, atLeastOnce()).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void report_notifiesPlatformAdmins() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User admin = User.builder().id(30L).name("Admin").email("admin@test.com")
                .password("enc").role(Role.PLATFORM_ADMIN).userType(UserType.PLATFORM)
                .active(true).build();
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of(admin));

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        verify(notificationEventProducer, atLeastOnce()).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void report_publishesActivityEvent() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        verify(vehicleActivityProducer).publish(any(VehicleActivityEvent.class));
    }

    @Test
    void report_vehicleSaved() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void report_userNotFoundThrows() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        var req = new BreakdownReportRequest();
        req.setVehicleId(1L);
        assertThatThrownBy(() -> breakdownService.report(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void report_breakdownSavedWithCompanyId() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(breakdownRepository.save(any())).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByRole(Role.PLATFORM_ADMIN)).thenReturn(List.of());

        var req = new BreakdownReportRequest();
        req.setVehicleId(1L); req.setLatitude(6.5244); req.setLongitude(3.3792);
        req.setDescription("Engine failure");

        breakdownService.report(req);
        ArgumentCaptor<BreakdownReport> captor = ArgumentCaptor.forClass(BreakdownReport.class);
        verify(breakdownRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany().getId()).isEqualTo(COMPANY_ID);
    }

    // ─── dispatchReplacement ──────────────────────────────────────────────────

    @Test
    void dispatchReplacement_setsStatusReplacementDispatched() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        breakdownService.dispatchReplacement(1L, req);
        assertThat(report.getStatus()).isEqualTo(BreakdownStatus.REPLACEMENT_DISPATCHED);
    }

    @Test
    void dispatchReplacement_replacementSetAssigned() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        breakdownService.dispatchReplacement(1L, req);
        assertThat(replacement.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
    }

    @Test
    void dispatchReplacement_notReportedStatusThrows() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        assertThatThrownBy(() -> breakdownService.dispatchReplacement(1L, req))
                .isInstanceOf(BreakdownException.class);
    }

    @Test
    void dispatchReplacement_replacementNotAvailableThrows() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.ASSIGNED);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        assertThatThrownBy(() -> breakdownService.dispatchReplacement(1L, req))
                .isInstanceOf(BreakdownException.class);
    }

    @Test
    void dispatchReplacement_notFoundThrows() {
        when(breakdownRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        assertThatThrownBy(() -> breakdownService.dispatchReplacement(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dispatchReplacement_replacementVehicleLinked() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        breakdownService.dispatchReplacement(1L, req);
        assertThat(report.getReplacementVehicle()).isEqualTo(replacement);
    }

    @Test
    void dispatchReplacement_sendsNotification() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        breakdownService.dispatchReplacement(1L, req);
        verify(notificationEventProducer, atLeastOnce()).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void dispatchReplacement_returnsResponse() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        assertThat(breakdownService.dispatchReplacement(1L, req)).isNotNull();
    }

    @Test
    void dispatchReplacement_noActivityProducerInteraction() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        breakdownService.dispatchReplacement(1L, req);
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void dispatchReplacement_notificationTypeCorrect() {
        Vehicle originalVehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        Vehicle replacement = buildVehicle(2L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, originalVehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(replacement));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(fieldStaff));
        when(tripRequestRepository.save(any())).thenReturn(mock(TripRequest.class));
        when(vehicleRepository.save(replacement)).thenReturn(replacement);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchReplacementRequest();
        req.setReplacementVehicleId(2L);
        req.setStaffId(USER_ID);
        breakdownService.dispatchReplacement(1L, req);

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues().get(0).getType()).isEqualTo("REPLACEMENT_DISPATCHED");
    }

    // ─── dispatchCrew ─────────────────────────────────────────────────────────

    @Test
    void dispatchCrew_setsStatusCrewDispatched() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(maintenanceFlagRepository.save(any())).thenReturn(mock(MaintenanceFlag.class));
        when(userRepository.save(crew)).thenReturn(crew);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        breakdownService.dispatchCrew(1L, req);
        assertThat(report.getStatus()).isEqualTo(BreakdownStatus.CREW_DISPATCHED);
    }

    @Test
    void dispatchCrew_crewSetUnavailable() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(maintenanceFlagRepository.save(any())).thenReturn(mock(MaintenanceFlag.class));
        when(userRepository.save(crew)).thenReturn(crew);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        breakdownService.dispatchCrew(1L, req);
        assertThat(crew.getAvailable()).isFalse();
    }

    @Test
    void dispatchCrew_notReportedOrReplacementDispatchedThrows() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.RESOLVED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> breakdownService.dispatchCrew(1L, req))
                .isInstanceOf(BreakdownException.class);
    }

    @Test
    void dispatchCrew_nonCrewRoleThrows() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User notCrew = buildUser(5L, Role.FLEET_MANAGER);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(notCrew));
        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> breakdownService.dispatchCrew(1L, req))
                .isInstanceOf(BreakdownException.class);
    }

    @Test
    void dispatchCrew_crewNotAvailableThrows() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        crew.setAvailable(false);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> breakdownService.dispatchCrew(1L, req))
                .isInstanceOf(BreakdownException.class);
    }

    @Test
    void dispatchCrew_maintenanceFlagCreated() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(maintenanceFlagRepository.save(any())).thenReturn(mock(MaintenanceFlag.class));
        when(userRepository.save(crew)).thenReturn(crew);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        breakdownService.dispatchCrew(1L, req);
        verify(maintenanceFlagRepository).save(any(MaintenanceFlag.class));
    }

    @Test
    void dispatchCrew_sendsNotificationToCrew() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(maintenanceFlagRepository.save(any())).thenReturn(mock(MaintenanceFlag.class));
        when(userRepository.save(crew)).thenReturn(crew);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        breakdownService.dispatchCrew(1L, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void dispatchCrew_crewAssignedToReport() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(maintenanceFlagRepository.save(any())).thenReturn(mock(MaintenanceFlag.class));
        when(userRepository.save(crew)).thenReturn(crew);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        breakdownService.dispatchCrew(1L, req);
        assertThat(report.getAssignedCrew()).isEqualTo(crew);
    }

    @Test
    void dispatchCrew_replacementDispatchedStatusAlsoAccepted() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        User crew = buildCrewUser(5L);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPLACEMENT_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(maintenanceFlagRepository.save(any())).thenReturn(mock(MaintenanceFlag.class));
        when(userRepository.save(crew)).thenReturn(crew);
        when(breakdownRepository.save(report)).thenReturn(report);

        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        assertThatCode(() -> breakdownService.dispatchCrew(1L, req)).doesNotThrowAnyException();
    }

    @Test
    void dispatchCrew_notFoundThrows() {
        when(breakdownRepository.findById(999L)).thenReturn(Optional.empty());
        var req = new DispatchCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> breakdownService.dispatchCrew(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── resolve ──────────────────────────────────────────────────────────────

    @Test
    void resolve_setsStatusResolved() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        breakdownService.resolve(1L);
        assertThat(report.getStatus()).isEqualTo(BreakdownStatus.RESOLVED);
    }

    @Test
    void resolve_vehicleSetAvailable() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        breakdownService.resolve(1L);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void resolve_alreadyResolvedThrows() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.AVAILABLE);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.RESOLVED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        assertThatThrownBy(() -> breakdownService.resolve(1L)).isInstanceOf(BreakdownException.class);
    }

    @Test
    void resolve_notFoundThrows() {
        when(breakdownRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> breakdownService.resolve(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolve_setsResolvedAt() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        breakdownService.resolve(1L);
        assertThat(report.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_publishesActivityEvent() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        breakdownService.resolve(1L);
        verify(vehicleActivityProducer).publish(any(VehicleActivityEvent.class));
    }

    @Test
    void resolve_returnsResponse() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        assertThat(breakdownService.resolve(1L)).isNotNull();
    }

    @Test
    void resolve_breakdownSaved() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        breakdownService.resolve(1L);
        verify(breakdownRepository).save(report);
    }

    @Test
    void resolve_noNotificationSideEffect() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        when(breakdownRepository.save(report)).thenReturn(report);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        breakdownService.resolve(1L);
        verifyNoInteractions(notificationEventProducer);
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_companyUserReturnsCompanyReport() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        assertThat(breakdownService.getById(1L)).isNotNull();
    }

    @Test
    void getById_platformUserCallsFindById() {
        TenantContext.set(null, USER_ID, "PLATFORM_ADMIN", "PLATFORM");
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findById(1L)).thenReturn(Optional.of(report));
        breakdownService.getById(1L);
        verify(breakdownRepository).findById(1L);
        verify(breakdownRepository, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    void getById_notFoundThrows() {
        when(breakdownRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> breakdownService.getById(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_returnsCorrectId() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        assertThat(breakdownService.getById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getById_neverSaves() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        breakdownService.getById(1L);
        verify(breakdownRepository, never()).save(any());
    }

    @Test
    void getById_noNotificationInteraction() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        breakdownService.getById(1L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getById_mapsStatus() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        BreakdownReport report = buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff);

        when(breakdownRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(report));
        assertThat(breakdownService.getById(1L).getStatus()).isEqualTo(BreakdownStatus.REPORTED);
    }

    // ─── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsCompanyReports() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        when(breakdownRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(List.of(buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff)));
        assertThat(breakdownService.getAll()).hasSize(1);
    }

    @Test
    void getAll_emptyList() {
        when(breakdownRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        assertThat(breakdownService.getAll()).isEmpty();
    }

    @Test
    void getAll_usesCompanyId() {
        when(breakdownRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        breakdownService.getAll();
        verify(breakdownRepository).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getAll_neverSaves() {
        when(breakdownRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        breakdownService.getAll();
        verify(breakdownRepository, never()).save(any());
    }

    @Test
    void getAll_noNotificationInteraction() {
        when(breakdownRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        breakdownService.getAll();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAll_multipleReports() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        when(breakdownRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff),
                buildReport(2L, BreakdownStatus.CREW_DISPATCHED, vehicle, fieldStaff)));
        assertThat(breakdownService.getAll()).hasSize(2);
    }

    // ─── getAllPlatform ────────────────────────────────────────────────────────

    @Test
    void getAllPlatform_returnsAll() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        when(breakdownRepository.findAll()).thenReturn(List.of(
                buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff)));
        assertThat(breakdownService.getAllPlatform()).hasSize(1);
    }

    @Test
    void getAllPlatform_emptyList() {
        when(breakdownRepository.findAll()).thenReturn(List.of());
        assertThat(breakdownService.getAllPlatform()).isEmpty();
    }

    @Test
    void getAllPlatform_callsFindAll() {
        when(breakdownRepository.findAll()).thenReturn(List.of());
        breakdownService.getAllPlatform();
        verify(breakdownRepository).findAll();
    }

    @Test
    void getAllPlatform_neverSaves() {
        when(breakdownRepository.findAll()).thenReturn(List.of());
        breakdownService.getAllPlatform();
        verify(breakdownRepository, never()).save(any());
    }

    @Test
    void getAllPlatform_doesNotCallFindByCompanyId() {
        when(breakdownRepository.findAll()).thenReturn(List.of());
        breakdownService.getAllPlatform();
        verify(breakdownRepository, never()).findByCompanyId(any());
    }

    @Test
    void getAllPlatform_noNotificationInteraction() {
        when(breakdownRepository.findAll()).thenReturn(List.of());
        breakdownService.getAllPlatform();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAllPlatform_noActivityProducerInteraction() {
        when(breakdownRepository.findAll()).thenReturn(List.of());
        breakdownService.getAllPlatform();
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void getAllPlatform_multipleReports() {
        Vehicle vehicle = buildVehicle(1L, VehicleStatus.BROKEN_DOWN);
        User fieldStaff = buildUser(USER_ID, Role.FIELD_STAFF);
        when(breakdownRepository.findAll()).thenReturn(List.of(
                buildReport(1L, BreakdownStatus.REPORTED, vehicle, fieldStaff),
                buildReport(2L, BreakdownStatus.RESOLVED, vehicle, fieldStaff)));
        assertThat(breakdownService.getAllPlatform()).hasSize(2);
    }
}
