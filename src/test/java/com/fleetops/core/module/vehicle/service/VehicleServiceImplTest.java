package com.fleetops.core.module.vehicle.service;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.mileage.repository.MileageLogRepository;
import com.fleetops.core.module.vehicle.dto.MilestoneIntervalRequest;
import com.fleetops.core.module.vehicle.dto.VehicleRequest;
import com.fleetops.core.module.vehicle.dto.VehicleResponse;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.impl.VehicleServiceImpl;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
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
class VehicleServiceImplTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock CompanyRepository companyRepository;
    @Mock MileageLogRepository mileageLogRepository;
    @Mock MaintenanceFlagRepository maintenanceFlagRepository;
    @Mock com.fleetops.core.module.triprequest.repository.TripRequestRepository tripRequestRepository;
    @Mock com.fleetops.core.module.vehicle.repository.VehicleImageRepository vehicleImageRepository;

    @InjectMocks VehicleServiceImpl vehicleService;

    private static final Long COMPANY_ID = 10L;
    private static final double DEFAULT_INTERVAL = 10000.0;

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, 1L, "FLEET_MANAGER", "COMPANY");
        ReflectionTestUtils.setField(vehicleService, "defaultMilestoneInterval", DEFAULT_INTERVAL);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Company buildCompany() {
        return Company.builder().id(COMPANY_ID).name("Fleet Co").build();
    }

    private Vehicle buildVehicle(Long id, String plate) {
        return Vehicle.builder()
                .id(id).company(buildCompany()).make("Toyota").model("Camry")
                .plateNumber(plate).currentMileage(0.0).milestoneInterval(DEFAULT_INTERVAL)
                .purchasePrice(new BigDecimal("5000000")).purchaseDate(LocalDate.now().minusYears(2))
                .totalMaintenanceSpend(BigDecimal.ZERO).breakdownCount(0)
                .maxMileage(300000.0).maxTrips(500).maxMaintenanceRounds(30)
                .status(VehicleStatus.AVAILABLE).lifecyclePercentage(0.0).markedForSale(false)
                .build();
    }

    private VehicleRequest buildRequest(String plate) {
        VehicleRequest req = new VehicleRequest();
        req.setMake("Toyota"); req.setModel("Camry"); req.setPlateNumber(plate);
        req.setPurchaseDate(LocalDate.now().minusYears(1));
        req.setPurchasePrice(new BigDecimal("5000000"));
        return req;
    }

    // ─── registerVehicle ──────────────────────────────────────────────────────

    @Test
    void registerVehicle_returnsResponse() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        assertThat(vehicleService.registerVehicle(buildRequest("ABC-123-XY"))).isNotNull();
    }

    @Test
    void registerVehicle_duplicatePlateThrows() {
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(true);
        assertThatThrownBy(() -> vehicleService.registerVehicle(buildRequest("ABC-123-XY")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerVehicle_duplicatePlateNeverSaves() {
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(true);
        assertThatThrownBy(() -> vehicleService.registerVehicle(buildRequest("ABC-123-XY")));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void registerVehicle_plateNumberUpperCased() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);

        VehicleRequest req = buildRequest("abc-123-xy");
        vehicleService.registerVehicle(req);
        verify(vehicleRepository).existsByPlateNumber("ABC-123-XY");
    }

    @Test
    void registerVehicle_companyNotFoundThrows() {
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vehicleService.registerVehicle(buildRequest("ABC-123-XY")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerVehicle_usesDefaultMilestoneWhenNull() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);

        VehicleRequest req = buildRequest("ABC-123-XY");
        req.setMilestoneInterval(null);
        vehicleService.registerVehicle(req);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getMilestoneInterval()).isEqualTo(DEFAULT_INTERVAL);
    }

    @Test
    void registerVehicle_usesCustomMilestoneInterval() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        vehicle.setMilestoneInterval(5000.0);
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);

        VehicleRequest req = buildRequest("ABC-123-XY");
        req.setMilestoneInterval(5000.0);
        vehicleService.registerVehicle(req);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getMilestoneInterval()).isEqualTo(5000.0);
    }

    @Test
    void registerVehicle_linkedToCorrectCompany() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        vehicleService.registerVehicle(buildRequest("ABC-123-XY"));

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany().getId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void registerVehicle_makeSetCorrectly() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);

        VehicleRequest req = buildRequest("ABC-123-XY");
        req.setMake("Honda");
        vehicleService.registerVehicle(req);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getMake()).isEqualTo("Honda");
    }

    @Test
    void registerVehicle_saveCalledOnce() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        vehicleService.registerVehicle(buildRequest("ABC-123-XY"));
        verify(vehicleRepository, times(1)).save(any());
    }

    @Test
    void registerVehicle_usesCompanyIdFromTenantContext() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        vehicleService.registerVehicle(buildRequest("ABC-123-XY"));
        verify(companyRepository).findById(COMPANY_ID);
    }

    @Test
    void registerVehicle_responseContainsPlate() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        var result = vehicleService.registerVehicle(buildRequest("ABC-123-XY"));
        assertThat(result.getPlateNumber()).isEqualTo("ABC-123-XY");
    }

    @Test
    void registerVehicle_trimmedPlate() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);

        VehicleRequest req = buildRequest("  abc-123-xy  ");
        vehicleService.registerVehicle(req);
        verify(vehicleRepository).existsByPlateNumber("ABC-123-XY");
    }

    @Test
    void registerVehicle_noMaintenanceFlagInteraction() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        vehicleService.registerVehicle(buildRequest("ABC-123-XY"));
        verifyNoInteractions(maintenanceFlagRepository);
    }

    @Test
    void registerVehicle_noMileageLogInteraction() {
        Company company = buildCompany();
        Vehicle vehicle = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.existsByPlateNumber("ABC-123-XY")).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.save(any())).thenReturn(vehicle);
        vehicleService.registerVehicle(buildRequest("ABC-123-XY"));
        verifyNoInteractions(mileageLogRepository);
    }

    // ─── getAllVehicles ───────────────────────────────────────────────────────

    @Test
    void getAllVehicles_companyUserReturnsCompanyVehicles() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(v));
        assertThat(vehicleService.getAllVehicles()).hasSize(1);
    }

    @Test
    void getAllVehicles_platformAdminReturnsAll() {
        TenantContext.set(null, 1L, "PLATFORM_ADMIN", "PLATFORM");
        Vehicle v1 = buildVehicle(1L, "ABC-123-XY");
        Vehicle v2 = buildVehicle(2L, "DEF-456-ZY");
        when(vehicleRepository.findAll()).thenReturn(List.of(v1, v2));
        assertThat(vehicleService.getAllVehicles()).hasSize(2);
    }

    @Test
    void getAllVehicles_platformCallsFindAll() {
        TenantContext.set(null, 1L, "PLATFORM_ADMIN", "PLATFORM");
        when(vehicleRepository.findAll()).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verify(vehicleRepository).findAll();
        verify(vehicleRepository, never()).findByCompanyId(any());
    }

    @Test
    void getAllVehicles_companyCallsFindByCompanyId() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verify(vehicleRepository).findByCompanyId(COMPANY_ID);
        verify(vehicleRepository, never()).findAll();
    }

    @Test
    void getAllVehicles_emptyList() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        assertThat(vehicleService.getAllVehicles()).isEmpty();
    }

    @Test
    void getAllVehicles_mapsPlateNumbers() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(v));
        assertThat(vehicleService.getAllVehicles().get(0).getPlateNumber()).isEqualTo("ABC-123-XY");
    }

    @Test
    void getAllVehicles_neverSaves() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getAllVehicles_noMaintenanceFlagInteraction() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verifyNoInteractions(maintenanceFlagRepository);
    }

    @Test
    void getAllVehicles_noMileageLogInteraction() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verifyNoInteractions(mileageLogRepository);
    }

    @Test
    void getAllVehicles_multipleVehicles() {
        List<Vehicle> vehicles = List.of(buildVehicle(1L, "A"), buildVehicle(2L, "B"), buildVehicle(3L, "C"));
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(vehicles);
        assertThat(vehicleService.getAllVehicles()).hasSize(3);
    }

    @Test
    void getAllVehicles_mapsIds() {
        Vehicle v = buildVehicle(42L, "ABC-123-XY");
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(v));
        assertThat(vehicleService.getAllVehicles().get(0).getId()).isEqualTo(42L);
    }

    @Test
    void getAllVehicles_findByCompanyIdCalledOnce() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verify(vehicleRepository, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getAllVehicles_noCompanyRepoInteraction() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        vehicleService.getAllVehicles();
        verifyNoInteractions(companyRepository);
    }

    // ─── getAvailableVehicles ─────────────────────────────────────────────────

    @Test
    void getAvailableVehicles_returnsAvailable() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of(v));
        assertThat(vehicleService.getAvailableVehicles()).hasSize(1);
    }

    @Test
    void getAvailableVehicles_emptyWhenNone() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        assertThat(vehicleService.getAvailableVehicles()).isEmpty();
    }

    @Test
    void getAvailableVehicles_usesAvailableStatus() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verify(vehicleRepository).findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE);
    }

    @Test
    void getAvailableVehicles_usesCompanyIdFromTenant() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verify(vehicleRepository).findByCompanyIdAndStatus(eq(COMPANY_ID), any());
    }

    @Test
    void getAvailableVehicles_neverSaves() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getAvailableVehicles_noCompanyRepoInteraction() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getAvailableVehicles_mapsPlateNumbers() {
        Vehicle v = buildVehicle(1L, "XYZ-789-AB");
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of(v));
        assertThat(vehicleService.getAvailableVehicles().get(0).getPlateNumber()).isEqualTo("XYZ-789-AB");
    }

    @Test
    void getAvailableVehicles_multipleReturned() {
        List<Vehicle> vehicles = List.of(buildVehicle(1L, "A"), buildVehicle(2L, "B"));
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(vehicles);
        assertThat(vehicleService.getAvailableVehicles()).hasSize(2);
    }

    @Test
    void getAvailableVehicles_noMaintenanceFlagInteraction() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verifyNoInteractions(maintenanceFlagRepository);
    }

    @Test
    void getAvailableVehicles_noMileageLogInteraction() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verifyNoInteractions(mileageLogRepository);
    }

    @Test
    void getAvailableVehicles_calledExactlyOnce() {
        when(vehicleRepository.findByCompanyIdAndStatus(COMPANY_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of());
        vehicleService.getAvailableVehicles();
        verify(vehicleRepository, times(1)).findByCompanyIdAndStatus(any(), any());
    }

    // ─── getVehicleById ───────────────────────────────────────────────────────

    @Test
    void getVehicleById_returnsVehicle() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        assertThat(vehicleService.getVehicleById(1L)).isNotNull();
    }

    @Test
    void getVehicleById_notFoundThrows() {
        when(vehicleRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vehicleService.getVehicleById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getVehicleById_returnsCorrectPlate() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        assertThat(vehicleService.getVehicleById(1L).getPlateNumber()).isEqualTo("ABC-123-XY");
    }

    @Test
    void getVehicleById_lookupByCorrectId() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        vehicleService.getVehicleById(1L);
        verify(vehicleRepository).findByIdAndCompanyId(1L, COMPANY_ID);
    }

    @Test
    void getVehicleById_neverSaves() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        vehicleService.getVehicleById(1L);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getVehicleById_noCompanyRepoInteraction() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        vehicleService.getVehicleById(1L);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void getVehicleById_noMaintenanceFlagInteraction() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        vehicleService.getVehicleById(1L);
        verifyNoInteractions(maintenanceFlagRepository);
    }

    @Test
    void getVehicleById_returnsCorrectId() {
        Vehicle v = buildVehicle(42L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(42L, COMPANY_ID)).thenReturn(Optional.of(v));
        assertThat(vehicleService.getVehicleById(42L).getId()).isEqualTo(42L);
    }

    @Test
    void getVehicleById_mapsStatus() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        v.setStatus(VehicleStatus.MAINTENANCE);
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        assertThat(vehicleService.getVehicleById(1L).getStatus()).isEqualTo("MAINTENANCE");
    }

    @Test
    void getVehicleById_companyIdUsedForScoping() {
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vehicleService.getVehicleById(1L));
        verify(vehicleRepository).findByIdAndCompanyId(1L, COMPANY_ID);
    }

    // ─── updateMilestoneInterval ──────────────────────────────────────────────

    @Test
    void updateMilestoneInterval_updatesInterval() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        vehicleService.updateMilestoneInterval(1L, req);
        assertThat(v.getMilestoneInterval()).isEqualTo(8000.0);
    }

    @Test
    void updateMilestoneInterval_savesCalled() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        vehicleService.updateMilestoneInterval(1L, req);
        verify(vehicleRepository).save(v);
    }

    @Test
    void updateMilestoneInterval_notFoundThrows() {
        when(vehicleRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vehicleService.updateMilestoneInterval(99L, new MilestoneIntervalRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMilestoneInterval_notFoundNeverSaves() {
        when(vehicleRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vehicleService.updateMilestoneInterval(99L, new MilestoneIntervalRequest()));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void updateMilestoneInterval_returnsResponse() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        assertThat(vehicleService.updateMilestoneInterval(1L, req)).isNotNull();
    }

    @Test
    void updateMilestoneInterval_saveCalledOnce() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        vehicleService.updateMilestoneInterval(1L, req);
        verify(vehicleRepository, times(1)).save(any());
    }

    @Test
    void updateMilestoneInterval_noCompanyRepoInteraction() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        vehicleService.updateMilestoneInterval(1L, req);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void updateMilestoneInterval_noMaintenanceFlagInteraction() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        vehicleService.updateMilestoneInterval(1L, req);
        verifyNoInteractions(maintenanceFlagRepository);
    }

    @Test
    void updateMilestoneInterval_lookupByIdAndCompanyId() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);

        var req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(8000.0);
        vehicleService.updateMilestoneInterval(1L, req);
        verify(vehicleRepository).findByIdAndCompanyId(1L, COMPANY_ID);
    }

    @Test
    void updateMilestoneInterval_differentIntervals() {
        Vehicle v1 = buildVehicle(1L, "A");
        Vehicle v2 = buildVehicle(2L, "B");
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(v1));
        when(vehicleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(v2));
        when(vehicleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req1 = new MilestoneIntervalRequest(); req1.setMilestoneInterval(5000.0);
        var req2 = new MilestoneIntervalRequest(); req2.setMilestoneInterval(15000.0);
        vehicleService.updateMilestoneInterval(1L, req1);
        vehicleService.updateMilestoneInterval(2L, req2);

        assertThat(v1.getMilestoneInterval()).isEqualTo(5000.0);
        assertThat(v2.getMilestoneInterval()).isEqualTo(15000.0);
    }

    // ─── recomputeHealthForVehicle ────────────────────────────────────────────

    @Test
    void recomputeHealth_setsHealthScore() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthScore()).isNotNull();
    }

    @Test
    void recomputeHealth_setsHealthGrade() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthGrade()).isNotNull();
    }

    @Test
    void recomputeHealth_brandNewVehicleExcellent() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        v.setPurchaseDate(LocalDate.now());
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthScore()).isGreaterThan(85.0);
        assertThat(v.getHealthGrade()).isEqualTo("EXCELLENT");
    }

    @Test
    void recomputeHealth_setsHealthComputedAt() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthComputedAt()).isNotNull();
    }

    @Test
    void recomputeHealth_setsLifecyclePercentage() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getLifecyclePercentage()).isNotNull();
    }

    @Test
    void recomputeHealth_highMileageLowersScore() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        v.setCurrentMileage(280000.0);
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthScore()).isLessThan(100.0);
    }

    @Test
    void recomputeHealth_highLifecycleMarksForSale() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        v.setCurrentMileage(270000.0);
        v.setBreakdownCount(4);
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(25L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(425L);
        vehicleService.recomputeHealthForVehicle(v);
        if (v.getLifecyclePercentage() >= 80.0) {
            assertThat(v.getMarkedForSale()).isTrue();
            assertThat(v.getStatus()).isEqualTo(VehicleStatus.OUT_OF_SERVICE);
        }
    }

    @Test
    void recomputeHealth_queriesMaintenanceFlags() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        verify(maintenanceFlagRepository).countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED);
    }

    @Test
    void recomputeHealth_queriesMileageLogs() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        verify(mileageLogRepository).countQualifiedTripsByVehicleId(1L);
    }

    @Test
    void recomputeHealth_scoreNotNegative() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        v.setCurrentMileage(300000.0);
        v.setBreakdownCount(10);
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(30L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(500L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthScore()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void recomputeHealth_noCompanyRepoInteraction() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void recomputeHealth_gradeIsOneOfFive() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthGrade()).isIn("EXCELLENT", "GOOD", "FAIR", "POOR", "CRITICAL");
    }

    @Test
    void recomputeHealth_scoreAtMost100() {
        Vehicle v = buildVehicle(1L, "ABC-123-XY");
        when(maintenanceFlagRepository.countByVehicleIdAndStatus(1L, FlagStatus.RESOLVED)).thenReturn(0L);
        when(mileageLogRepository.countQualifiedTripsByVehicleId(1L)).thenReturn(0L);
        vehicleService.recomputeHealthForVehicle(v);
        assertThat(v.getHealthScore()).isLessThanOrEqualTo(100.0);
    }
}
