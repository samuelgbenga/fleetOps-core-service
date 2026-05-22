package com.fleetops.core.module.admin.service;

import com.fleetops.core.module.admin.dto.PlatformDashboardSummaryResponse;
import com.fleetops.core.module.admin.dto.UtilisationReportResponse;
import com.fleetops.core.module.admin.dto.VehicleHealthReportResponse;
import com.fleetops.core.module.admin.service.impl.AdminReportServiceImpl;
import com.fleetops.core.module.breakdown.repository.BreakdownRepository;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.model.CompanyStatus;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceImplTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private UserRepository userRepository;
    @Mock private MaintenanceFlagRepository maintenanceFlagRepository;
    @Mock private TripRequestRepository tripRequestRepository;
    @Mock private BreakdownRepository breakdownRepository;

    @InjectMocks
    private AdminReportServiceImpl adminReportService;

    private static final Long COMPANY_ID = 1L;

    @BeforeEach
    void stubDefaults() {
        TenantContext.set(COMPANY_ID, 1L, "FLEET_MANAGER", "COMPANY");
        lenient().when(companyRepository.count()).thenReturn(0L);
        lenient().when(companyRepository.findAllByStatus(any())).thenReturn(Collections.emptyList());
        lenient().when(companyRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(buildCompany(COMPANY_ID, "Default Co")));
        lenient().when(vehicleRepository.count()).thenReturn(0L);
        lenient().when(vehicleRepository.findByCompanyId(any())).thenReturn(Collections.emptyList());
        lenient().when(userRepository.count()).thenReturn(0L);
        lenient().when(maintenanceFlagRepository.findByStatus(any())).thenReturn(Collections.emptyList());
        lenient().when(breakdownRepository.count()).thenReturn(0L);
        lenient().when(tripRequestRepository.countByCompanyIdAndStatus(any(), any())).thenReturn(0L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Company buildCompany(Long id, String name) {
        return Company.builder().id(id).name(name).email(name.toLowerCase().replace(" ", "") + "@corp.com").build();
    }

    private Vehicle buildVehicle(Long id, VehicleStatus status, Double healthScore) {
        return Vehicle.builder()
                .id(id)
                .make("Toyota")
                .model("Camry")
                .plateNumber("PLT-" + id)
                .status(status)
                .healthScore(healthScore)
                .markedForSale(false)
                .build();
    }

    // ========================= getPlatformDashboardSummary =========================

    @Test
    void getPlatformDashboardSummary_returnsTotalCompaniesFromCount() {
        when(companyRepository.count()).thenReturn(5L);

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalCompanies()).isEqualTo(5L);
    }

    @Test
    void getPlatformDashboardSummary_returnsActiveCompaniesFromApprovedList() {
        when(companyRepository.findAllByStatus(CompanyStatus.APPROVED))
                .thenReturn(List.of(buildCompany(1L, "Alpha"), buildCompany(2L, "Beta")));

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getActiveCompanies()).isEqualTo(2L);
    }

    @Test
    void getPlatformDashboardSummary_returnsPendingCompaniesFromPendingList() {
        when(companyRepository.findAllByStatus(CompanyStatus.PENDING))
                .thenReturn(List.of(buildCompany(3L, "PendingCo")));

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getPendingCompanies()).isEqualTo(1L);
    }

    @Test
    void getPlatformDashboardSummary_returnsTotalVehiclesFromCount() {
        when(vehicleRepository.count()).thenReturn(30L);

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalVehicles()).isEqualTo(30L);
    }

    @Test
    void getPlatformDashboardSummary_returnsTotalUsersFromCount() {
        when(userRepository.count()).thenReturn(50L);

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalUsers()).isEqualTo(50L);
    }

    @Test
    void getPlatformDashboardSummary_returnsTotalOpenFlagsFromOpenStatus() {
        MaintenanceFlag flag1 = mock(MaintenanceFlag.class);
        MaintenanceFlag flag2 = mock(MaintenanceFlag.class);
        when(maintenanceFlagRepository.findByStatus(FlagStatus.OPEN)).thenReturn(List.of(flag1, flag2));

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalOpenFlags()).isEqualTo(2L);
    }

    @Test
    void getPlatformDashboardSummary_returnsTotalBreakdownsFromCount() {
        when(breakdownRepository.count()).thenReturn(7L);

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalBreakdowns()).isEqualTo(7L);
    }

    @Test
    void getPlatformDashboardSummary_returnsTotalResolvedFlagsFromResolvedStatus() {
        when(maintenanceFlagRepository.findByStatus(FlagStatus.RESOLVED))
                .thenReturn(List.of(mock(MaintenanceFlag.class)));

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalResolvedFlags()).isEqualTo(1L);
    }

    @Test
    void getPlatformDashboardSummary_allZeroWhenEmpty() {
        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalCompanies()).isZero();
        assertThat(result.getActiveCompanies()).isZero();
        assertThat(result.getPendingCompanies()).isZero();
        assertThat(result.getTotalVehicles()).isZero();
        assertThat(result.getTotalUsers()).isZero();
        assertThat(result.getTotalOpenFlags()).isZero();
        assertThat(result.getTotalBreakdowns()).isZero();
        assertThat(result.getTotalResolvedFlags()).isZero();
    }

    @Test
    void getPlatformDashboardSummary_returnsNonNullResponseInstance() {
        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result).isNotNull().isInstanceOf(PlatformDashboardSummaryResponse.class);
    }

    @Test
    void getPlatformDashboardSummary_callsCompanyRepositoryCountOnce() {
        adminReportService.getPlatformDashboardSummary();

        verify(companyRepository, times(1)).count();
    }

    @Test
    void getPlatformDashboardSummary_callsVehicleRepositoryCountOnce() {
        adminReportService.getPlatformDashboardSummary();

        verify(vehicleRepository, times(1)).count();
    }

    @Test
    void getPlatformDashboardSummary_callsUserRepositoryCountOnce() {
        adminReportService.getPlatformDashboardSummary();

        verify(userRepository, times(1)).count();
    }

    @Test
    void getPlatformDashboardSummary_callsBreakdownRepositoryCountOnce() {
        adminReportService.getPlatformDashboardSummary();

        verify(breakdownRepository, times(1)).count();
    }

    @Test
    void getPlatformDashboardSummary_activeCountDoesNotIncludePendingCompanies() {
        when(companyRepository.findAllByStatus(CompanyStatus.APPROVED))
                .thenReturn(List.of(buildCompany(1L, "Active")));
        when(companyRepository.findAllByStatus(CompanyStatus.PENDING))
                .thenReturn(List.of(buildCompany(2L, "Pending")));

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getActiveCompanies()).isEqualTo(1L);
        assertThat(result.getPendingCompanies()).isEqualTo(1L);
    }

    @Test
    void getPlatformDashboardSummary_largeNumbersHandled() {
        when(companyRepository.count()).thenReturn(1_000_000L);
        when(vehicleRepository.count()).thenReturn(5_000_000L);
        when(userRepository.count()).thenReturn(10_000_000L);
        when(breakdownRepository.count()).thenReturn(500_000L);

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalCompanies()).isEqualTo(1_000_000L);
        assertThat(result.getTotalVehicles()).isEqualTo(5_000_000L);
        assertThat(result.getTotalUsers()).isEqualTo(10_000_000L);
        assertThat(result.getTotalBreakdowns()).isEqualTo(500_000L);
    }

    @Test
    void getPlatformDashboardSummary_queriesOpenAndResolvedFlagsSeparately() {
        when(maintenanceFlagRepository.findByStatus(FlagStatus.OPEN))
                .thenReturn(List.of(mock(MaintenanceFlag.class)));
        when(maintenanceFlagRepository.findByStatus(FlagStatus.RESOLVED))
                .thenReturn(List.of(mock(MaintenanceFlag.class), mock(MaintenanceFlag.class)));

        PlatformDashboardSummaryResponse result = adminReportService.getPlatformDashboardSummary();

        assertThat(result.getTotalOpenFlags()).isEqualTo(1L);
        assertThat(result.getTotalResolvedFlags()).isEqualTo(2L);
    }

    // ========================= getUtilisationReport =========================

    @Test
    void getUtilisationReport_returnsNonNullResponse() {
        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result).isNotNull();
    }

    @Test
    void getUtilisationReport_mapsCompanyIdCorrectly() {
        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void getUtilisationReport_mapsCompanyNameCorrectly() {
        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getCompanyName()).isEqualTo("Default Co");
    }

    @Test
    void getUtilisationReport_countsTotalVehiclesCorrectly() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, null),
                buildVehicle(2L, VehicleStatus.ASSIGNED, null),
                buildVehicle(3L, VehicleStatus.MAINTENANCE, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getTotalVehicles()).isEqualTo(3L);
    }

    @Test
    void getUtilisationReport_countsAvailableVehiclesCorrectly() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, null),
                buildVehicle(2L, VehicleStatus.AVAILABLE, null),
                buildVehicle(3L, VehicleStatus.ASSIGNED, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getAvailableVehicles()).isEqualTo(2L);
    }

    @Test
    void getUtilisationReport_countsAssignedVehiclesCorrectly() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.ASSIGNED, null),
                buildVehicle(2L, VehicleStatus.ASSIGNED, null),
                buildVehicle(3L, VehicleStatus.AVAILABLE, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getAssignedVehicles()).isEqualTo(2L);
    }

    @Test
    void getUtilisationReport_countsMaintenanceVehiclesCorrectly() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.MAINTENANCE, null),
                buildVehicle(2L, VehicleStatus.AVAILABLE, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getMaintenanceVehicles()).isEqualTo(1L);
    }

    @Test
    void getUtilisationReport_totalTripsIsSumOfAllStatuses() {
        when(tripRequestRepository.countByCompanyIdAndStatus(COMPANY_ID, TripRequestStatus.COMPLETED)).thenReturn(5L);
        when(tripRequestRepository.countByCompanyIdAndStatus(COMPANY_ID, TripRequestStatus.PENDING)).thenReturn(3L);
        when(tripRequestRepository.countByCompanyIdAndStatus(COMPANY_ID, TripRequestStatus.APPROVED)).thenReturn(2L);
        when(tripRequestRepository.countByCompanyIdAndStatus(COMPANY_ID, TripRequestStatus.REJECTED)).thenReturn(1L);

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getTotalTrips()).isEqualTo(11L);
    }

    @Test
    void getUtilisationReport_completedTripsCounted() {
        when(tripRequestRepository.countByCompanyIdAndStatus(COMPANY_ID, TripRequestStatus.COMPLETED)).thenReturn(4L);

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getCompletedTrips()).isEqualTo(4L);
    }

    @Test
    void getUtilisationReport_utilizationRateCalculatedFromAssignedOverTotal() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.ASSIGNED, null),
                buildVehicle(2L, VehicleStatus.ASSIGNED, null),
                buildVehicle(3L, VehicleStatus.AVAILABLE, null),
                buildVehicle(4L, VehicleStatus.AVAILABLE, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getUtilizationRate()).isEqualTo(50.0);
    }

    @Test
    void getUtilisationReport_utilizationRateIsZeroWhenNoVehicles() {
        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getUtilizationRate()).isEqualTo(0.0);
    }

    @Test
    void getUtilisationReport_utilizationRateIs100WhenAllVehiclesAssigned() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.ASSIGNED, null),
                buildVehicle(2L, VehicleStatus.ASSIGNED, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getUtilizationRate()).isEqualTo(100.0);
    }

    @Test
    void getUtilisationReport_throwsExceptionWhenCompanyNotFound() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminReportService.getUtilisationReport())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUtilisationReport_callsVehicleRepositoryOnce() {
        adminReportService.getUtilisationReport();

        verify(vehicleRepository, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getUtilisationReport_utilizationRateRoundedToTwoDecimals() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.ASSIGNED, null),
                buildVehicle(2L, VehicleStatus.AVAILABLE, null),
                buildVehicle(3L, VehicleStatus.AVAILABLE, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        // 1/3 * 100 = 33.333...; Math.round(33.33 * 100) / 100.0 = 33.33
        assertThat(result.getUtilizationRate()).isEqualTo(33.33);
    }

    @Test
    void getUtilisationReport_maintenanceVehiclesNotCountedAsAssigned() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.MAINTENANCE, null),
                buildVehicle(2L, VehicleStatus.MAINTENANCE, null)
        ));

        UtilisationReportResponse result = adminReportService.getUtilisationReport();

        assertThat(result.getAssignedVehicles()).isZero();
        assertThat(result.getUtilizationRate()).isEqualTo(0.0);
    }

    // ========================= getVehicleHealthReport =========================

    @Test
    void getVehicleHealthReport_returnsNonNullResponse() {
        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result).isNotNull();
    }

    @Test
    void getVehicleHealthReport_mapsCompanyIdCorrectly() {
        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void getVehicleHealthReport_mapsCompanyNameCorrectly() {
        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getCompanyName()).isEqualTo("Default Co");
    }

    @Test
    void getVehicleHealthReport_countsTotalVehiclesCorrectly() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 90.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 60.0),
                buildVehicle(3L, VehicleStatus.AVAILABLE, 20.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getTotalVehicles()).isEqualTo(3L);
    }

    @Test
    void getVehicleHealthReport_computesAvgHealthScore() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 90.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 70.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getAvgHealthScore()).isEqualTo(80.0);
    }

    @Test
    void getVehicleHealthReport_countsCriticalVehicles_scoreLessThan31() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 10.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 30.0),
                buildVehicle(3L, VehicleStatus.AVAILABLE, 31.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getCriticalCount()).isEqualTo(2L);
    }

    @Test
    void getVehicleHealthReport_countsPoorVehicles_score31to50() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 31.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 50.0),
                buildVehicle(3L, VehicleStatus.AVAILABLE, 51.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getPoorCount()).isEqualTo(2L);
    }

    @Test
    void getVehicleHealthReport_countsFairVehicles_score51to70() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 51.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 70.0),
                buildVehicle(3L, VehicleStatus.AVAILABLE, 71.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getFairCount()).isEqualTo(2L);
    }

    @Test
    void getVehicleHealthReport_countsGoodVehicles_score71to85() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 71.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 85.0),
                buildVehicle(3L, VehicleStatus.AVAILABLE, 86.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getGoodCount()).isEqualTo(2L);
    }

    @Test
    void getVehicleHealthReport_countsExcellentVehicles_score86AndAbove() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 86.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, 100.0),
                buildVehicle(3L, VehicleStatus.AVAILABLE, 85.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getExcellentCount()).isEqualTo(2L);
    }

    @Test
    void getVehicleHealthReport_countsMarkedForSaleVehicles() {
        Vehicle v1 = buildVehicle(1L, VehicleStatus.AVAILABLE, 90.0);
        v1.setMarkedForSale(true);
        Vehicle v2 = buildVehicle(2L, VehicleStatus.AVAILABLE, 70.0);
        v2.setMarkedForSale(true);
        Vehicle v3 = buildVehicle(3L, VehicleStatus.AVAILABLE, 60.0);
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(v1, v2, v3));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getMarkedForSaleCount()).isEqualTo(2L);
    }

    @Test
    void getVehicleHealthReport_excludesNullHealthScoreFromAvgCalculation() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 80.0),
                buildVehicle(2L, VehicleStatus.AVAILABLE, null)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getAvgHealthScore()).isEqualTo(80.0);
    }

    @Test
    void getVehicleHealthReport_avgHealthScoreIsZeroWhenNoScoredVehicles() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, null),
                buildVehicle(2L, VehicleStatus.AVAILABLE, null)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getAvgHealthScore()).isEqualTo(0.0);
    }

    @Test
    void getVehicleHealthReport_throwsExceptionWhenCompanyNotFound() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminReportService.getVehicleHealthReport())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getVehicleHealthReport_emptyCompanyHasAllZeroCounts() {
        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getTotalVehicles()).isZero();
        assertThat(result.getAvgHealthScore()).isEqualTo(0.0);
        assertThat(result.getCriticalCount()).isZero();
        assertThat(result.getPoorCount()).isZero();
        assertThat(result.getFairCount()).isZero();
        assertThat(result.getGoodCount()).isZero();
        assertThat(result.getExcellentCount()).isZero();
        assertThat(result.getMarkedForSaleCount()).isZero();
    }

    @Test
    void getVehicleHealthReport_countsAreZeroForGradesWithNoMatchingVehicles() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, 90.0)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getCriticalCount()).isZero();
        assertThat(result.getPoorCount()).isZero();
        assertThat(result.getFairCount()).isZero();
        assertThat(result.getGoodCount()).isZero();
        assertThat(result.getExcellentCount()).isEqualTo(1L);
    }

    @Test
    void getVehicleHealthReport_nullHealthScoreNotCountedInAnyGradeBucket() {
        when(vehicleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(
                buildVehicle(1L, VehicleStatus.AVAILABLE, null)
        ));

        VehicleHealthReportResponse result = adminReportService.getVehicleHealthReport();

        assertThat(result.getTotalVehicles()).isEqualTo(1L);
        assertThat(result.getCriticalCount()).isZero();
        assertThat(result.getPoorCount()).isZero();
        assertThat(result.getFairCount()).isZero();
        assertThat(result.getGoodCount()).isZero();
        assertThat(result.getExcellentCount()).isZero();
    }
}
