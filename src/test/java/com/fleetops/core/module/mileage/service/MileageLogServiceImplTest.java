package com.fleetops.core.module.mileage.service;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.mileage.dto.MileageLogRequest;
import com.fleetops.core.module.mileage.dto.MileageLogResponse;
import com.fleetops.core.module.mileage.model.MileageLog;
import com.fleetops.core.module.mileage.repository.MileageLogRepository;
import com.fleetops.core.module.mileage.service.impl.MileageLogServiceImpl;
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
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MileageLogServiceImplTest {

    @Mock MileageLogRepository mileageLogRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock TripRequestRepository tripRequestRepository;
    @Mock MaintenanceEventProducer maintenanceEventProducer;
    @Mock VehicleActivityProducer vehicleActivityProducer;
    @Mock VehicleService vehicleService;

    @InjectMocks MileageLogServiceImpl mileageLogService;

    private static final Long COMPANY_ID = 10L;
    private static final String EMAIL = "driver@test.com";

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, 1L, "FLEET_MANAGER", "COMPANY");
        var auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Company buildCompany() {
        return Company.builder().id(COMPANY_ID).name("Fleet Co").build();
    }

    private Vehicle buildVehicle(Long id, double currentMileage) {
        return Vehicle.builder()
                .id(id).company(buildCompany()).plateNumber("ABC-123-XY")
                .currentMileage(currentMileage).milestoneInterval(10000.0)
                .maxMileage(300000.0).maxTrips(500).maxMaintenanceRounds(30)
                .purchasePrice(new BigDecimal("5000000"))
                .purchaseDate(LocalDate.now().minusYears(2))
                .totalMaintenanceSpend(BigDecimal.ZERO).breakdownCount(0)
                .status(VehicleStatus.AVAILABLE).lifecyclePercentage(0.0).markedForSale(false)
                .make("Toyota").model("Camry").build();
    }

    private User buildUser() {
        return User.builder().id(1L).name("Driver").email(EMAIL)
                .password("enc").role(Role.FIELD_STAFF).userType(UserType.COMPANY)
                .company(buildCompany()).active(true).build();
    }

    private MileageLog buildLog(Long id, Vehicle vehicle) {
        return MileageLog.builder().id(id).company(buildCompany()).vehicle(vehicle)
                .submittedBy(buildUser()).reportedMileage(5000.0).build();
    }

    // ─── submit ───────────────────────────────────────────────────────────────

    @Test
    void submit_returnsResponse() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);

        assertThat(mileageLogService.submit(req)).isNotNull();
    }

    @Test
    void submit_mileageLowerThanCurrentThrows() {
        Vehicle vehicle = buildVehicle(1L, 5000.0);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(3000.0);
        assertThatThrownBy(() -> mileageLogService.submit(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void submit_mileageEqualToCurrentThrows() {
        Vehicle vehicle = buildVehicle(1L, 5000.0);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        assertThatThrownBy(() -> mileageLogService.submit(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void submit_mileageNeverSavedWhenInvalid() {
        Vehicle vehicle = buildVehicle(1L, 5000.0);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(3000.0);
        assertThatThrownBy(() -> mileageLogService.submit(req));
        verify(mileageLogRepository, never()).save(any());
    }

    @Test
    void submit_updatesVehicleMileage() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);
        assertThat(vehicle.getCurrentMileage()).isEqualTo(5000.0);
    }

    @Test
    void submit_callsRecomputeHealth() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);
        verify(vehicleService).recomputeHealthForVehicle(vehicle);
    }

    @Test
    void submit_publishesActivityEvent() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);
        verify(vehicleActivityProducer).publish(any(VehicleActivityEvent.class));
    }

    @Test
    void submit_companyNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());
        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        assertThatThrownBy(() -> mileageLogService.submit(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submit_vehicleNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.empty());
        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        assertThatThrownBy(() -> mileageLogService.submit(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submit_userNotFoundThrows() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        assertThatThrownBy(() -> mileageLogService.submit(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submit_milestoneReachedPublishesMaintenanceEvent() {
        Vehicle vehicle = buildVehicle(1L, 9500.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(10500.0);
        mileageLogService.submit(req);
        verify(maintenanceEventProducer).publish(any(MaintenanceFlagCreatedEvent.class));
    }

    @Test
    void submit_milestoneNotReachedNoMaintenanceEvent() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);
        verifyNoInteractions(maintenanceEventProducer);
    }

    @Test
    void submit_savedLogHasCorrectMileage() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);

        ArgumentCaptor<MileageLog> captor = ArgumentCaptor.forClass(MileageLog.class);
        verify(mileageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getReportedMileage()).isEqualTo(5000.0);
    }

    @Test
    void submit_vehicleSavedTwice() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);
        verify(vehicleRepository, times(2)).save(vehicle);
    }

    @Test
    void submit_usesCompanyIdFromTenantContext() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0);
        mileageLogService.submit(req);
        verify(vehicleRepository).findByIdAndCompanyId(1L, COMPANY_ID);
    }

    @Test
    void submit_tripRequestLinkedWhenProvided() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tripRequestRepository.findById(5L)).thenReturn(Optional.empty());
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0); req.setTripRequestId(5L);
        mileageLogService.submit(req);
        verify(tripRequestRepository).findById(5L);
    }

    @Test
    void submit_nullTripRequestNotQueried() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        User user = buildUser();
        MileageLog log = buildLog(1L, vehicle);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(mileageLogRepository.save(any())).thenReturn(log);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new MileageLogRequest();
        req.setVehicleId(1L); req.setReportedMileage(5000.0); req.setTripRequestId(null);
        mileageLogService.submit(req);
        verifyNoInteractions(tripRequestRepository);
    }

    // ─── getByVehicle ─────────────────────────────────────────────────────────

    @Test
    void getByVehicle_returnsList() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        MileageLog log = buildLog(1L, vehicle);
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of(log));
        assertThat(mileageLogService.getByVehicle(1L)).hasSize(1);
    }

    @Test
    void getByVehicle_emptyList() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        assertThat(mileageLogService.getByVehicle(1L)).isEmpty();
    }

    @Test
    void getByVehicle_usesVehicleIdAndCompanyId() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verify(mileageLogRepository).findByVehicleIdAndCompanyId(1L, COMPANY_ID);
    }

    @Test
    void getByVehicle_neverSaves() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verify(mileageLogRepository, never()).save(any());
    }

    @Test
    void getByVehicle_noVehicleRepoInteraction() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getByVehicle_noActivityProducerInteraction() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void getByVehicle_noMaintenanceProducerInteraction() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verifyNoInteractions(maintenanceEventProducer);
    }

    @Test
    void getByVehicle_multipleLogsReturned() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(
                List.of(buildLog(1L, vehicle), buildLog(2L, vehicle)));
        assertThat(mileageLogService.getByVehicle(1L)).hasSize(2);
    }

    @Test
    void getByVehicle_mapsReportedMileage() {
        Vehicle vehicle = buildVehicle(1L, 1000.0);
        MileageLog log = buildLog(1L, vehicle);
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of(log));
        assertThat(mileageLogService.getByVehicle(1L).get(0).getReportedMileage()).isEqualTo(5000.0);
    }

    @Test
    void getByVehicle_calledOnce() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verify(mileageLogRepository, times(1)).findByVehicleIdAndCompanyId(any(), any());
    }

    @Test
    void getByVehicle_noCompanyRepoInteraction() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getByVehicle_noUserRepoInteraction() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getByVehicle_noVehicleServiceInteraction() {
        when(mileageLogRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        mileageLogService.getByVehicle(1L);
        verifyNoInteractions(vehicleService);
    }

    @Test
    void getByVehicle_differentVehicleIds() {
        Vehicle vehicle = buildVehicle(2L, 1000.0);
        MileageLog log = buildLog(1L, vehicle);
        when(mileageLogRepository.findByVehicleIdAndCompanyId(2L, COMPANY_ID)).thenReturn(List.of(log));
        assertThat(mileageLogService.getByVehicle(2L)).hasSize(1);
        verify(mileageLogRepository).findByVehicleIdAndCompanyId(2L, COMPANY_ID);
    }
}
