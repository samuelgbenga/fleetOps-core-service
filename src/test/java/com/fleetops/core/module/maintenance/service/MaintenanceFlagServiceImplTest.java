package com.fleetops.core.module.maintenance.service;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.maintenance.dto.*;
import com.fleetops.core.module.maintenance.model.*;
import com.fleetops.core.module.maintenance.repository.*;
import com.fleetops.core.module.maintenance.service.impl.MaintenanceFlagServiceImpl;
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
import com.fleetops.core.shared.exception.QuotationException;
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
class MaintenanceFlagServiceImplTest {

    @Mock MaintenanceFlagRepository flagRepository;
    @Mock MaintenanceQuotationRepository quotationRepository;
    @Mock MaintenanceRatingRepository ratingRepository;
    @Mock MaintenanceMessageRepository messageRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock NotificationEventProducer notificationEventProducer;
    @Mock VehicleActivityProducer vehicleActivityProducer;
    @Mock VehicleService vehicleService;

    @InjectMocks MaintenanceFlagServiceImpl maintenanceFlagService;

    private static final Long COMPANY_ID = 10L;
    private static final Long FLAG_ID = 100L;
    private static final String EMAIL = "manager@test.com";

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, 1L, "FLEET_MANAGER", "COMPANY");
        var auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        lenient().when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(buildUser(1L, EMAIL, Role.FLEET_MANAGER)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Company buildCompany() {
        return Company.builder().id(COMPANY_ID).name("Fleet Co").build();
    }

    private Vehicle buildVehicle(Long id) {
        return Vehicle.builder().id(id).company(buildCompany()).plateNumber("ABC-123-XY")
                .currentMileage(0.0).milestoneInterval(10000.0)
                .maxMileage(300000.0).maxTrips(500).maxMaintenanceRounds(30)
                .purchasePrice(new BigDecimal("5000000"))
                .purchaseDate(LocalDate.now().minusYears(2))
                .totalMaintenanceSpend(BigDecimal.ZERO).breakdownCount(0)
                .status(VehicleStatus.MAINTENANCE).lifecyclePercentage(0.0).markedForSale(false)
                .make("Toyota").model("Camry").build();
    }

    private User buildUser(Long id, String email, Role role) {
        return User.builder().id(id).name("User").email(email)
                .password("enc").role(role).userType(UserType.COMPANY)
                .company(buildCompany()).active(true).available(true).build();
    }

    private User buildCrewUser(Long id) {
        return User.builder().id(id).name("Crew").email("crew@test.com")
                .password("enc").role(Role.MAINTENANCE_CREW).userType(UserType.COMPANY)
                .company(buildCompany()).active(true).available(true).totalJobsCompleted(0).build();
    }

    private MaintenanceFlag buildFlag(Long id, FlagStatus status) {
        return MaintenanceFlag.builder()
                .id(id).company(buildCompany()).vehicle(buildVehicle(1L))
                .triggerType(TriggerType.MANUAL).status(status)
                .description("Test maintenance").build();
    }

    private MaintenanceFlag buildFlagWithCrew(Long id, FlagStatus status, User crew) {
        return MaintenanceFlag.builder()
                .id(id).company(buildCompany()).vehicle(buildVehicle(1L))
                .triggerType(TriggerType.MANUAL).status(status)
                .assignedCrew(crew).description("Test maintenance").build();
    }

    private MaintenanceQuotation buildQuotation(Long id, MaintenanceFlag flag, User crew) {
        return MaintenanceQuotation.builder()
                .id(id).flag(flag).crew(crew).company(buildCompany())
                .estimatedCost(new BigDecimal("50000")).description("Fix it")
                .revisionNumber(1).status(QuotationStatus.PENDING).build();
    }

    // ─── createFlag ───────────────────────────────────────────────────────────

    @Test
    void createFlag_returnsResponse() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        assertThat(maintenanceFlagService.createFlag(req)).isNotNull();
    }

    @Test
    void createFlag_statusIsOpen() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);

        ArgumentCaptor<MaintenanceFlag> captor = ArgumentCaptor.forClass(MaintenanceFlag.class);
        verify(flagRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FlagStatus.OPEN);
    }

    @Test
    void createFlag_vehicleSetToMaintenance() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    }

    @Test
    void createFlag_vehicleSaved() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void createFlag_publishesActivityEvent() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);
        verify(vehicleActivityProducer).publish(any(VehicleActivityEvent.class));
    }

    @Test
    void createFlag_companyNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());
        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Desc");
        assertThatThrownBy(() -> maintenanceFlagService.createFlag(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFlag_vehicleNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.empty());
        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Desc");
        assertThatThrownBy(() -> maintenanceFlagService.createFlag(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFlag_userNotFoundThrows() {
        Vehicle vehicle = buildVehicle(1L);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Desc");
        assertThatThrownBy(() -> maintenanceFlagService.createFlag(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFlag_triggerTypeIsManual() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);

        ArgumentCaptor<MaintenanceFlag> captor = ArgumentCaptor.forClass(MaintenanceFlag.class);
        verify(flagRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo(TriggerType.MANUAL);
    }

    @Test
    void createFlag_saveCalledOnce() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);
        verify(flagRepository, times(1)).save(any());
    }

    @Test
    void createFlag_noNotificationSideEffect() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Needs repair");
        maintenanceFlagService.createFlag(req);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void createFlag_descriptionSetCorrectly() {
        User requester = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        Vehicle vehicle = buildVehicle(1L);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany()));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(flagRepository.save(any())).thenReturn(flag);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        var req = new CreateFlagRequest();
        req.setVehicleId(1L); req.setDescription("Engine issue");
        maintenanceFlagService.createFlag(req);

        ArgumentCaptor<MaintenanceFlag> captor = ArgumentCaptor.forClass(MaintenanceFlag.class);
        verify(flagRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Engine issue");
    }

    // ─── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsCompanyFlags() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        assertThat(maintenanceFlagService.getAll()).hasSize(1);
    }

    @Test
    void getAll_emptyList() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        assertThat(maintenanceFlagService.getAll()).isEmpty();
    }

    @Test
    void getAll_usesCompanyId() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verify(flagRepository).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getAll_neverSaves() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verify(flagRepository, never()).save(any());
    }

    @Test
    void getAll_noNotificationInteraction() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAll_multipleFlags() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildFlag(1L, FlagStatus.OPEN), buildFlag(2L, FlagStatus.CREW_ASSIGNED)));
        assertThat(maintenanceFlagService.getAll()).hasSize(2);
    }

    @Test
    void getAll_findByCompanyIdCalledOnce() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verify(flagRepository, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getAll_noActivityProducerInteraction() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void getAll_noVehicleRepoInteraction() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getAll_noUserRepoInteraction() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verifyNoInteractions(userRepository);
    }

    @Test
    void getAll_mapsIds() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(buildFlag(42L, FlagStatus.OPEN)));
        assertThat(maintenanceFlagService.getAll().get(0).getId()).isEqualTo(42L);
    }

    @Test
    void getAll_mapsStatuses() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildFlag(1L, FlagStatus.CREW_ASSIGNED)));
        assertThat(maintenanceFlagService.getAll().get(0).getStatus())
                .isEqualTo(FlagStatus.CREW_ASSIGNED.name());
    }

    @Test
    void getAll_noRatingRepoInteraction() {
        when(flagRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getAll();
        verifyNoInteractions(ratingRepository);
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_returnsFlag() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        assertThat(maintenanceFlagService.getById(FLAG_ID)).isNotNull();
    }

    @Test
    void getById_notFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_returnsCorrectId() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        assertThat(maintenanceFlagService.getById(FLAG_ID).getId()).isEqualTo(FLAG_ID);
    }

    @Test
    void getById_usesCompanyIdFromTenant() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        maintenanceFlagService.getById(FLAG_ID);
        verify(flagRepository).findByIdAndCompanyId(FLAG_ID, COMPANY_ID);
    }

    @Test
    void getById_neverSaves() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        maintenanceFlagService.getById(FLAG_ID);
        verify(flagRepository, never()).save(any());
    }

    @Test
    void getById_noNotificationInteraction() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        maintenanceFlagService.getById(FLAG_ID);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getById_noVehicleRepoInteraction() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        maintenanceFlagService.getById(FLAG_ID);
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getById_doesNotSaveAnyUser() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        maintenanceFlagService.getById(FLAG_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getById_mapsStatus() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID))
                .thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.QUOTE_SUBMITTED)));
        assertThat(maintenanceFlagService.getById(FLAG_ID).getStatus())
                .isEqualTo(FlagStatus.QUOTE_SUBMITTED.name());
    }

    // ─── getByVehicle ─────────────────────────────────────────────────────────

    @Test
    void getByVehicle_returnsList() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID))
                .thenReturn(List.of(buildFlag(FLAG_ID, FlagStatus.OPEN)));
        assertThat(maintenanceFlagService.getByVehicle(1L)).hasSize(1);
    }

    @Test
    void getByVehicle_emptyList() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        assertThat(maintenanceFlagService.getByVehicle(1L)).isEmpty();
    }

    @Test
    void getByVehicle_usesVehicleIdAndCompanyId() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verify(flagRepository).findByVehicleIdAndCompanyId(1L, COMPANY_ID);
    }

    @Test
    void getByVehicle_neverSaves() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verify(flagRepository, never()).save(any());
    }

    @Test
    void getByVehicle_multipleFlags() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(
                List.of(buildFlag(1L, FlagStatus.OPEN), buildFlag(2L, FlagStatus.RESOLVED)));
        assertThat(maintenanceFlagService.getByVehicle(1L)).hasSize(2);
    }

    @Test
    void getByVehicle_noNotificationInteraction() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getByVehicle_noUserRepoInteraction() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getByVehicle_calledOnce() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verify(flagRepository, times(1)).findByVehicleIdAndCompanyId(any(), any());
    }

    @Test
    void getByVehicle_noVehicleRepoInteraction() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getByVehicle_noActivityProducerInteraction() {
        when(flagRepository.findByVehicleIdAndCompanyId(1L, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.getByVehicle(1L);
        verifyNoInteractions(vehicleActivityProducer);
    }

    // ─── getMyCrew ────────────────────────────────────────────────────────────

    @Test
    void getMyCrew_returnsCrewFlags() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of(buildFlag(FLAG_ID, FlagStatus.CREW_ASSIGNED)));
        assertThat(maintenanceFlagService.getMyCrew()).hasSize(1);
    }

    @Test
    void getMyCrew_emptyList() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        assertThat(maintenanceFlagService.getMyCrew()).isEmpty();
    }

    @Test
    void getMyCrew_userNotFoundThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.getMyCrew())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMyCrew_usesCrewId() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        maintenanceFlagService.getMyCrew();
        verify(flagRepository).findByAssignedCrewId(5L);
    }

    @Test
    void getMyCrew_neverSaves() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        maintenanceFlagService.getMyCrew();
        verify(flagRepository, never()).save(any());
    }

    @Test
    void getMyCrew_noNotificationInteraction() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        maintenanceFlagService.getMyCrew();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getMyCrew_noVehicleRepoInteraction() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        maintenanceFlagService.getMyCrew();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getMyCrew_multipleFlags() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(
                List.of(buildFlag(1L, FlagStatus.CREW_ASSIGNED), buildFlag(2L, FlagStatus.IN_PROGRESS)));
        assertThat(maintenanceFlagService.getMyCrew()).hasSize(2);
    }

    @Test
    void getMyCrew_noCompanyRepoInteraction() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        maintenanceFlagService.getMyCrew();
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getMyCrew_noActivityProducerInteraction() {
        User crew = buildCrewUser(5L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(flagRepository.findByAssignedCrewId(5L)).thenReturn(List.of());
        maintenanceFlagService.getMyCrew();
        verifyNoInteractions(vehicleActivityProducer);
    }

    // ─── assignCrew ───────────────────────────────────────────────────────────

    @Test
    void assignCrew_setsStatusCrewAssigned() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.CREW_ASSIGNED);
    }

    @Test
    void assignCrew_crewSetUnavailable() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        assertThat(crew.getAvailable()).isFalse();
    }

    @Test
    void assignCrew_flagNotOpenThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.CREW_ASSIGNED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> maintenanceFlagService.assignCrew(FLAG_ID, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void assignCrew_flagNotFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> maintenanceFlagService.assignCrew(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignCrew_nonCrewRoleThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User notCrew = buildUser(5L, "other@test.com", Role.FLEET_MANAGER);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(notCrew));

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        assertThatThrownBy(() -> maintenanceFlagService.assignCrew(FLAG_ID, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignCrew_crewAssignedToFlag() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        assertThat(flag.getAssignedCrew()).isEqualTo(crew);
    }

    @Test
    void assignCrew_sendsNotification() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void assignCrew_setsAssignedAt() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        assertThat(flag.getAssignedAt()).isNotNull();
    }

    @Test
    void assignCrew_flagSaved() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        verify(flagRepository).save(flag);
    }

    @Test
    void assignCrew_crewSaved() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);
        verify(userRepository).save(crew);
    }

    @Test
    void assignCrew_notificationTypeCrewAssigned() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        User crew = buildCrewUser(5L);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findById(5L)).thenReturn(Optional.of(crew));
        when(userRepository.save(crew)).thenReturn(crew);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new AssignCrewRequest();
        req.setCrewId(5L);
        maintenanceFlagService.assignCrew(FLAG_ID, req);

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("CREW_ASSIGNED");
    }

    // ─── submitQuotation ──────────────────────────────────────────────────────

    @Test
    void submitQuotation_returnsResponse() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.CREW_ASSIGNED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findById(FLAG_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(quotationRepository.save(any())).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("50000")); req.setDescription("Fix it");
        assertThat(maintenanceFlagService.submitQuotation(FLAG_ID, req)).isNotNull();
    }

    @Test
    void submitQuotation_flagSetToQuoteSubmitted() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.CREW_ASSIGNED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findById(FLAG_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(quotationRepository.save(any())).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("50000")); req.setDescription("Fix it");
        maintenanceFlagService.submitQuotation(FLAG_ID, req);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.QUOTE_SUBMITTED);
    }

    @Test
    void submitQuotation_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.submitQuotation(FLAG_ID, new QuotationRequest()))
                .isInstanceOf(QuotationException.class);
    }

    @Test
    void submitQuotation_notFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.submitQuotation(999L, new QuotationRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitQuotation_quotationSaved() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.CREW_ASSIGNED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findById(FLAG_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(quotationRepository.save(any())).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("50000")); req.setDescription("Fix it");
        maintenanceFlagService.submitQuotation(FLAG_ID, req);
        verify(quotationRepository).save(any());
    }

    @Test
    void submitQuotation_revisionNumberIsOne() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.CREW_ASSIGNED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findById(FLAG_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(quotationRepository.save(any())).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("50000")); req.setDescription("Fix it");
        maintenanceFlagService.submitQuotation(FLAG_ID, req);

        ArgumentCaptor<MaintenanceQuotation> captor = ArgumentCaptor.forClass(MaintenanceQuotation.class);
        verify(quotationRepository).save(captor.capture());
        assertThat(captor.getValue().getRevisionNumber()).isEqualTo(1);
    }

    @Test
    void submitQuotation_notifiesFleetManagers() {
        User crew = buildCrewUser(5L);
        User manager = buildUser(10L, "fm@test.com", Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.CREW_ASSIGNED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findById(FLAG_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(quotationRepository.save(any())).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of(manager));

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("50000")); req.setDescription("Fix it");
        maintenanceFlagService.submitQuotation(FLAG_ID, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void submitQuotation_estimatedCostSetCorrectly() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.CREW_ASSIGNED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findById(FLAG_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(crew));
        when(quotationRepository.save(any())).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("75000")); req.setDescription("Fix it");
        maintenanceFlagService.submitQuotation(FLAG_ID, req);

        ArgumentCaptor<MaintenanceQuotation> captor = ArgumentCaptor.forClass(MaintenanceQuotation.class);
        verify(quotationRepository).save(captor.capture());
        assertThat(captor.getValue().getEstimatedCost()).isEqualByComparingTo(new BigDecimal("75000"));
    }

    @Test
    void submitQuotation_userNotFoundThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.submitQuotation(FLAG_ID, new QuotationRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── reviseQuotation ──────────────────────────────────────────────────────

    @Test
    void reviseQuotation_returnsResponse() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_REJECTED, crew);
        MaintenanceQuotation last = buildQuotation(1L, flag, crew);
        MaintenanceQuotation revised = buildQuotation(2L, flag, crew);
        revised.setRevisionNumber(2);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(last));
        when(quotationRepository.save(any())).thenReturn(revised);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("40000")); req.setDescription("Revised");
        assertThat(maintenanceFlagService.reviseQuotation(FLAG_ID, req)).isNotNull();
    }

    @Test
    void reviseQuotation_flagSetToQuoteSubmitted() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_REJECTED, crew);
        MaintenanceQuotation last = buildQuotation(1L, flag, crew);
        MaintenanceQuotation revised = buildQuotation(2L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(last));
        when(quotationRepository.save(any())).thenReturn(revised);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("40000")); req.setDescription("Revised");
        maintenanceFlagService.reviseQuotation(FLAG_ID, req);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.QUOTE_SUBMITTED);
    }

    @Test
    void reviseQuotation_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.CREW_ASSIGNED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.reviseQuotation(FLAG_ID, new QuotationRequest()))
                .isInstanceOf(QuotationException.class);
    }

    @Test
    void reviseQuotation_noExistingQuotationThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_REJECTED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.reviseQuotation(FLAG_ID, new QuotationRequest()))
                .isInstanceOf(QuotationException.class);
    }

    @Test
    void reviseQuotation_incrementsRevisionNumber() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_REJECTED, crew);
        MaintenanceQuotation last = buildQuotation(1L, flag, crew);
        last.setRevisionNumber(1);
        MaintenanceQuotation revised = buildQuotation(2L, flag, crew);
        revised.setRevisionNumber(2);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(last));
        when(quotationRepository.save(any())).thenReturn(revised);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("40000")); req.setDescription("Revised");
        maintenanceFlagService.reviseQuotation(FLAG_ID, req);

        ArgumentCaptor<MaintenanceQuotation> captor = ArgumentCaptor.forClass(MaintenanceQuotation.class);
        verify(quotationRepository).save(captor.capture());
        assertThat(captor.getValue().getRevisionNumber()).isEqualTo(2);
    }

    @Test
    void reviseQuotation_usesLastCrew() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_REJECTED, crew);
        MaintenanceQuotation last = buildQuotation(1L, flag, crew);
        MaintenanceQuotation revised = buildQuotation(2L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(last));
        when(quotationRepository.save(any())).thenReturn(revised);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("40000")); req.setDescription("Revised");
        maintenanceFlagService.reviseQuotation(FLAG_ID, req);

        ArgumentCaptor<MaintenanceQuotation> captor = ArgumentCaptor.forClass(MaintenanceQuotation.class);
        verify(quotationRepository).save(captor.capture());
        assertThat(captor.getValue().getCrew()).isEqualTo(crew);
    }

    @Test
    void reviseQuotation_notFoundFlagThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.reviseQuotation(999L, new QuotationRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reviseQuotation_quotationSaved() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_REJECTED, crew);
        MaintenanceQuotation last = buildQuotation(1L, flag, crew);
        MaintenanceQuotation revised = buildQuotation(2L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(last));
        when(quotationRepository.save(any())).thenReturn(revised);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new QuotationRequest();
        req.setEstimatedCost(new BigDecimal("40000")); req.setDescription("Revised");
        maintenanceFlagService.reviseQuotation(FLAG_ID, req);
        verify(quotationRepository).save(any());
    }

    // ─── approveQuotation ─────────────────────────────────────────────────────

    @Test
    void approveQuotation_flagSetToQuoteApproved() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        maintenanceFlagService.approveQuotation(FLAG_ID);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.QUOTE_APPROVED);
    }

    @Test
    void approveQuotation_quotationStatusApproved() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        maintenanceFlagService.approveQuotation(FLAG_ID);
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void approveQuotation_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.CREW_ASSIGNED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.approveQuotation(FLAG_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approveQuotation_notFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.approveQuotation(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approveQuotation_sendsNotification() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        maintenanceFlagService.approveQuotation(FLAG_ID);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void approveQuotation_quotationReviewedAtSet() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        maintenanceFlagService.approveQuotation(FLAG_ID);
        assertThat(quotation.getReviewedAt()).isNotNull();
    }

    @Test
    void approveQuotation_noQuotationThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_SUBMITTED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.approveQuotation(FLAG_ID))
                .isInstanceOf(QuotationException.class);
    }

    @Test
    void approveQuotation_notificationTypeCorrect() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        maintenanceFlagService.approveQuotation(FLAG_ID);
        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("QUOTATION_APPROVED");
    }

    @Test
    void approveQuotation_returnsResponse() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        assertThat(maintenanceFlagService.approveQuotation(FLAG_ID)).isNotNull();
    }

    // ─── rejectQuotation ──────────────────────────────────────────────────────

    @Test
    void rejectQuotation_flagSetToQuoteRejected() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Too expensive");
        maintenanceFlagService.rejectQuotation(FLAG_ID, req);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.QUOTE_REJECTED);
    }

    @Test
    void rejectQuotation_quotationStatusRejected() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Too expensive");
        maintenanceFlagService.rejectQuotation(FLAG_ID, req);
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.REJECTED);
    }

    @Test
    void rejectQuotation_rejectionReasonSet() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Too expensive");
        maintenanceFlagService.rejectQuotation(FLAG_ID, req);
        assertThat(quotation.getRejectionReason()).isEqualTo("Too expensive");
    }

    @Test
    void rejectQuotation_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.rejectQuotation(FLAG_ID, new RejectQuotationRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectQuotation_sendsNotification() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Reason");
        maintenanceFlagService.rejectQuotation(FLAG_ID, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void rejectQuotation_notFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.rejectQuotation(999L, new RejectQuotationRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectQuotation_notificationTypeCorrect() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Reason");
        maintenanceFlagService.rejectQuotation(FLAG_ID, req);

        ArgumentCaptor<NotificationRequestEvent> captor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("QUOTATION_REJECTED");
    }

    @Test
    void rejectQuotation_reviewedAtSet() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Reason");
        maintenanceFlagService.rejectQuotation(FLAG_ID, req);
        assertThat(quotation.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectQuotation_returnsResponse() {
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.QUOTE_SUBMITTED, crew);
        MaintenanceQuotation quotation = buildQuotation(1L, flag, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(FLAG_ID)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RejectQuotationRequest();
        req.setReason("Reason");
        assertThat(maintenanceFlagService.rejectQuotation(FLAG_ID, req)).isNotNull();
    }

    // ─── updateProgress ───────────────────────────────────────────────────────

    @Test
    void updateProgress_flagSetInProgress() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new ProgressUpdateRequest();
        req.setProgressNotes("50% done");
        maintenanceFlagService.updateProgress(FLAG_ID, req);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.IN_PROGRESS);
    }

    @Test
    void updateProgress_progressNotesSet() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new ProgressUpdateRequest();
        req.setProgressNotes("50% done");
        maintenanceFlagService.updateProgress(FLAG_ID, req);
        assertThat(flag.getProgressNotes()).isEqualTo("50% done");
    }

    @Test
    void updateProgress_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.updateProgress(FLAG_ID, new ProgressUpdateRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateProgress_notFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.updateProgress(999L, new ProgressUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProgress_inProgressStatusAlsoAccepted() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new ProgressUpdateRequest();
        req.setProgressNotes("80% done");
        assertThatCode(() -> maintenanceFlagService.updateProgress(FLAG_ID, req)).doesNotThrowAnyException();
    }

    @Test
    void updateProgress_flagSaved() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new ProgressUpdateRequest();
        req.setProgressNotes("50% done");
        maintenanceFlagService.updateProgress(FLAG_ID, req);
        verify(flagRepository).save(flag);
    }

    @Test
    void updateProgress_notifiesFleetManagers() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        User manager = buildUser(10L, "fm@test.com", Role.FLEET_MANAGER);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of(manager));

        var req = new ProgressUpdateRequest();
        req.setProgressNotes("50% done");
        maintenanceFlagService.updateProgress(FLAG_ID, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void updateProgress_returnsResponse() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        var req = new ProgressUpdateRequest();
        req.setProgressNotes("50% done");
        assertThat(maintenanceFlagService.updateProgress(FLAG_ID, req)).isNotNull();
    }

    // ─── markDone ─────────────────────────────────────────────────────────────

    @Test
    void markDone_flagSetPendingApproval() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());

        maintenanceFlagService.markDone(FLAG_ID);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.PENDING_APPROVAL);
    }

    @Test
    void markDone_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.OPEN);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.markDone(FLAG_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void markDone_notFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.markDone(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markDone_inProgressAlsoAccepted() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        assertThatCode(() -> maintenanceFlagService.markDone(FLAG_ID)).doesNotThrowAnyException();
    }

    @Test
    void markDone_flagSaved() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.markDone(FLAG_ID);
        verify(flagRepository).save(flag);
    }

    @Test
    void markDone_notifiesFleetManagers() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        User manager = buildUser(10L, "fm@test.com", Role.FLEET_MANAGER);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of(manager));
        maintenanceFlagService.markDone(FLAG_ID);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void markDone_returnsResponse() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        assertThat(maintenanceFlagService.markDone(FLAG_ID)).isNotNull();
    }

    @Test
    void markDone_noActivityProducerInteraction() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.markDone(FLAG_ID);
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void markDone_noVehicleRepoInteraction() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.QUOTE_APPROVED);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(flagRepository.save(flag)).thenReturn(flag);
        when(userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, COMPANY_ID)).thenReturn(List.of());
        maintenanceFlagService.markDone(FLAG_ID);
        verifyNoInteractions(vehicleRepository);
    }

    // ─── resolve ──────────────────────────────────────────────────────────────

    @Test
    void resolve_flagSetResolved() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5); req.setComment("Great job");

        maintenanceFlagService.resolve(FLAG_ID, req);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.RESOLVED);
    }

    @Test
    void resolve_vehicleSetAvailable() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5); req.setComment("Great");

        maintenanceFlagService.resolve(FLAG_ID, req);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void resolve_wrongStatusThrows() {
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        assertThatThrownBy(() -> maintenanceFlagService.resolve(FLAG_ID, new RatingRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolve_alreadyRatedThrows() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(true);

        assertThatThrownBy(() -> maintenanceFlagService.resolve(FLAG_ID, new RatingRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolve_crewSetAvailable() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        crew.setAvailable(false);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(4);
        maintenanceFlagService.resolve(FLAG_ID, req);
        assertThat(crew.getAvailable()).isTrue();
    }

    @Test
    void resolve_ratingSaved() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5);
        maintenanceFlagService.resolve(FLAG_ID, req);
        verify(ratingRepository).save(any());
    }

    @Test
    void resolve_setsResolvedAt() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5);
        maintenanceFlagService.resolve(FLAG_ID, req);
        assertThat(flag.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_callsRecomputeHealth() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5);
        maintenanceFlagService.resolve(FLAG_ID, req);
        verify(vehicleService).recomputeHealthForVehicle(vehicle);
    }

    @Test
    void resolve_sendsNotificationToCrew() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5);
        maintenanceFlagService.resolve(FLAG_ID, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void resolve_publishesActivityEvent() {
        User rater = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        User crew = buildCrewUser(5L);
        MaintenanceFlag flag = buildFlagWithCrew(FLAG_ID, FlagStatus.PENDING_APPROVAL, crew);
        Vehicle vehicle = flag.getVehicle();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(rater));
        when(ratingRepository.existsByFlagIdAndRatedByUserId(FLAG_ID, 1L)).thenReturn(false);
        when(ratingRepository.save(any())).thenReturn(mock(MaintenanceRating.class));
        when(ratingRepository.computeAverageRatingForCrew(5L)).thenReturn(4.5);
        when(ratingRepository.countByCrewId(5L)).thenReturn(1L);
        when(userRepository.save(crew)).thenReturn(crew);
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
        when(flagRepository.save(flag)).thenReturn(flag);

        var req = new RatingRequest();
        req.setStars(5);
        maintenanceFlagService.resolve(FLAG_ID, req);
        verify(vehicleActivityProducer).publish(any(VehicleActivityEvent.class));
    }

    // ─── sendMessage ──────────────────────────────────────────────────────────

    @Test
    void sendMessage_returnsResponse() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Hello").build();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenReturn(msg);

        var req = new MessageRequest();
        req.setMessage("Hello");
        assertThat(maintenanceFlagService.sendMessage(FLAG_ID, req)).isNotNull();
    }

    @Test
    void sendMessage_flagNotFoundThrows() {
        when(flagRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.sendMessage(999L, new MessageRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sendMessage_messageSaved() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Hello").build();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenReturn(msg);

        var req = new MessageRequest();
        req.setMessage("Hello");
        maintenanceFlagService.sendMessage(FLAG_ID, req);
        verify(messageRepository).save(any());
    }

    @Test
    void sendMessage_senderRoleSetCorrectly() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Hello").build();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenReturn(msg);

        var req = new MessageRequest();
        req.setMessage("Hello");
        maintenanceFlagService.sendMessage(FLAG_ID, req);

        ArgumentCaptor<MaintenanceMessage> captor = ArgumentCaptor.forClass(MaintenanceMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSenderRole()).isEqualTo("FLEET_MANAGER");
    }

    @Test
    void sendMessage_userNotFoundThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> maintenanceFlagService.sendMessage(FLAG_ID, new MessageRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sendMessage_noNotificationInteraction() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Hello").build();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenReturn(msg);

        var req = new MessageRequest();
        req.setMessage("Hello");
        maintenanceFlagService.sendMessage(FLAG_ID, req);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void sendMessage_flagLinkedToMessage() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Hello").build();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenReturn(msg);

        var req = new MessageRequest();
        req.setMessage("Hello");
        maintenanceFlagService.sendMessage(FLAG_ID, req);

        ArgumentCaptor<MaintenanceMessage> captor = ArgumentCaptor.forClass(MaintenanceMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getFlag()).isEqualTo(flag);
    }

    @Test
    void sendMessage_messageTextSet() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Need parts").build();

        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenReturn(msg);

        var req = new MessageRequest();
        req.setMessage("Need parts");
        maintenanceFlagService.sendMessage(FLAG_ID, req);

        ArgumentCaptor<MaintenanceMessage> captor = ArgumentCaptor.forClass(MaintenanceMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("Need parts");
    }

    // ─── getMessages ──────────────────────────────────────────────────────────

    @Test
    void getMessages_returnsList() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        MaintenanceMessage msg = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("User").senderRole("FLEET_MANAGER").message("Hello").build();
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of(msg));
        assertThat(maintenanceFlagService.getMessages(FLAG_ID)).hasSize(1);
    }

    @Test
    void getMessages_emptyList() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        assertThat(maintenanceFlagService.getMessages(FLAG_ID)).isEmpty();
    }

    @Test
    void getMessages_usesFlagId() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verify(messageRepository).findByFlagIdOrderBySentAtAsc(FLAG_ID);
    }

    @Test
    void getMessages_neverSaves() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void getMessages_noNotificationInteraction() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getMessages_flagLookedUp() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verify(flagRepository).findByIdAndCompanyId(FLAG_ID, COMPANY_ID);
    }

    @Test
    void getMessages_multipleMessages() {
        User sender = buildUser(1L, EMAIL, Role.FLEET_MANAGER);
        MaintenanceFlag flag = buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS);
        var m1 = MaintenanceMessage.builder().id(1L).flag(flag).sender(sender)
                .senderName("U").senderRole("FLEET_MANAGER").message("M1").build();
        var m2 = MaintenanceMessage.builder().id(2L).flag(flag).sender(sender)
                .senderName("U").senderRole("FLEET_MANAGER").message("M2").build();
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(flag));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of(m1, m2));
        assertThat(maintenanceFlagService.getMessages(FLAG_ID)).hasSize(2);
    }

    @Test
    void getMessages_noUserRepoInteraction() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getMessages_noVehicleRepoInteraction() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getMessages_calledOnce() {
        when(flagRepository.findByIdAndCompanyId(FLAG_ID, COMPANY_ID)).thenReturn(Optional.of(buildFlag(FLAG_ID, FlagStatus.IN_PROGRESS)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(FLAG_ID)).thenReturn(List.of());
        maintenanceFlagService.getMessages(FLAG_ID);
        verify(messageRepository, times(1)).findByFlagIdOrderBySentAtAsc(FLAG_ID);
    }

    // ─── getAllPlatform ────────────────────────────────────────────────────────

    @Test
    void getAllPlatform_returnsAll() {
        when(flagRepository.findAll()).thenReturn(
                List.of(buildFlag(1L, FlagStatus.OPEN), buildFlag(2L, FlagStatus.RESOLVED)));
        assertThat(maintenanceFlagService.getAllPlatform()).hasSize(2);
    }

    @Test
    void getAllPlatform_emptyList() {
        when(flagRepository.findAll()).thenReturn(List.of());
        assertThat(maintenanceFlagService.getAllPlatform()).isEmpty();
    }

    @Test
    void getAllPlatform_callsFindAll() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verify(flagRepository).findAll();
    }

    @Test
    void getAllPlatform_neverSaves() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verify(flagRepository, never()).save(any());
    }

    @Test
    void getAllPlatform_noNotificationInteraction() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAllPlatform_noCompanyRepoInteraction() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getAllPlatform_doesNotCallFindByCompanyId() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verify(flagRepository, never()).findByCompanyId(any());
    }

    @Test
    void getAllPlatform_mapsIds() {
        when(flagRepository.findAll()).thenReturn(List.of(buildFlag(42L, FlagStatus.OPEN)));
        assertThat(maintenanceFlagService.getAllPlatform().get(0).getId()).isEqualTo(42L);
    }

    @Test
    void getAllPlatform_mapsStatuses() {
        when(flagRepository.findAll()).thenReturn(List.of(buildFlag(1L, FlagStatus.RESOLVED)));
        assertThat(maintenanceFlagService.getAllPlatform().get(0).getStatus())
                .isEqualTo(FlagStatus.RESOLVED.name());
    }

    @Test
    void getAllPlatform_findAllCalledOnce() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verify(flagRepository, times(1)).findAll();
    }

    @Test
    void getAllPlatform_noVehicleRepoInteraction() {
        when(flagRepository.findAll()).thenReturn(List.of());
        maintenanceFlagService.getAllPlatform();
        verifyNoInteractions(vehicleRepository);
    }
}
