package com.fleetops.core.admin.service;

import com.fleetops.core.admin.dto.UtilisationReportResponse;
import com.fleetops.core.admin.dto.VehicleHealthResponse;
import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private TripRequestRepository tripRequestRepository;
    @Mock private MaintenanceFlagRepository maintenanceFlagRepository;

    @InjectMocks private AdminReportService adminReportService;

    // ── getUtilisationReport ─────────────────────────────────────────────────

    @Test
    void getUtilisationReport_returnsCorrectCounts() {
        when(vehicleRepository.count()).thenReturn(5L);
        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(vehicle(3, VehicleStatus.AVAILABLE));
        when(vehicleRepository.findByStatus(VehicleStatus.ASSIGNED)).thenReturn(vehicle(1, VehicleStatus.ASSIGNED));
        when(vehicleRepository.findByStatus(VehicleStatus.MAINTENANCE)).thenReturn(vehicle(1, VehicleStatus.MAINTENANCE));
        when(tripRequestRepository.count()).thenReturn(20L);
        when(tripRequestRepository.findByStatus(TripRequestStatus.PENDING)).thenReturn(pendingTrips(4));

        UtilisationReportResponse report = adminReportService.getUtilisationReport();

        assertThat(report.getTotalVehicles()).isEqualTo(5L);
        assertThat(report.getAvailableVehicles()).isEqualTo(3);
        assertThat(report.getAssignedVehicles()).isEqualTo(1);
        assertThat(report.getMaintenanceVehicles()).isEqualTo(1);
        assertThat(report.getTotalTripsAllTime()).isEqualTo(20L);
        assertThat(report.getPendingTripRequests()).isEqualTo(4);
    }

    @Test
    void getUtilisationReport_emptyFleet_returnsAllZeros() {
        when(vehicleRepository.count()).thenReturn(0L);
        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of());
        when(vehicleRepository.findByStatus(VehicleStatus.ASSIGNED)).thenReturn(List.of());
        when(vehicleRepository.findByStatus(VehicleStatus.MAINTENANCE)).thenReturn(List.of());
        when(tripRequestRepository.count()).thenReturn(0L);
        when(tripRequestRepository.findByStatus(TripRequestStatus.PENDING)).thenReturn(List.of());

        UtilisationReportResponse report = adminReportService.getUtilisationReport();

        assertThat(report.getTotalVehicles()).isZero();
        assertThat(report.getAvailableVehicles()).isZero();
        assertThat(report.getPendingTripRequests()).isZero();
    }

    // ── getVehicleHealthSummary ───────────────────────────────────────────────

    @Test
    void getVehicleHealthSummary_countsOnlyOpenFlags() {
        Vehicle v = vehicleSingle(1L, VehicleStatus.MAINTENANCE);

        // 2 open flags + 1 resolved = 2 open
        MaintenanceFlag open1 = MaintenanceFlag.builder().id(1L).vehicle(v)
                .mileageAtTrigger(5000.0).status(FlagStatus.OPEN).build();
        MaintenanceFlag open2 = MaintenanceFlag.builder().id(2L).vehicle(v)
                .mileageAtTrigger(10000.0).status(FlagStatus.IN_PROGRESS).build();
        MaintenanceFlag resolved = MaintenanceFlag.builder().id(3L).vehicle(v)
                .mileageAtTrigger(5000.0).status(FlagStatus.RESOLVED).build();

        when(vehicleRepository.findAll()).thenReturn(List.of(v));
        when(maintenanceFlagRepository.findByVehicleId(1L)).thenReturn(List.of(open1, open2, resolved));

        List<VehicleHealthResponse> summary = adminReportService.getVehicleHealthSummary();

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).getOpenMaintenanceFlags()).isEqualTo(2L);
        assertThat(summary.get(0).getPlateNumber()).isEqualTo("VEH-1");
        assertThat(summary.get(0).getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    }

    @Test
    void getVehicleHealthSummary_noFlags_returnsZeroOpenFlags() {
        Vehicle v = vehicleSingle(2L, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findAll()).thenReturn(List.of(v));
        when(maintenanceFlagRepository.findByVehicleId(2L)).thenReturn(List.of());

        List<VehicleHealthResponse> summary = adminReportService.getVehicleHealthSummary();

        assertThat(summary.get(0).getOpenMaintenanceFlags()).isZero();
    }

    @Test
    void getVehicleHealthSummary_allFlagsResolved_returnsZeroOpenFlags() {
        Vehicle v = vehicleSingle(3L, VehicleStatus.AVAILABLE);
        MaintenanceFlag resolved = MaintenanceFlag.builder().id(1L).vehicle(v)
                .mileageAtTrigger(5000.0).status(FlagStatus.RESOLVED).build();

        when(vehicleRepository.findAll()).thenReturn(List.of(v));
        when(maintenanceFlagRepository.findByVehicleId(3L)).thenReturn(List.of(resolved));

        List<VehicleHealthResponse> summary = adminReportService.getVehicleHealthSummary();

        assertThat(summary.get(0).getOpenMaintenanceFlags()).isZero();
    }

    @Test
    void getVehicleHealthSummary_noVehicles_returnsEmptyList() {
        when(vehicleRepository.findAll()).thenReturn(List.of());

        assertThat(adminReportService.getVehicleHealthSummary()).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<Vehicle> vehicle(int count, VehicleStatus status) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> vehicleSingle((long) i + 1, status))
                .toList();
    }

    private Vehicle vehicleSingle(Long id, VehicleStatus status) {
        return Vehicle.builder().id(id).make("Toyota").model("Camry")
                .plateNumber("VEH-" + id).status(status)
                .currentMileage(1000.0 * id).milestoneInterval(5000.0).build();
    }

    private List<TripRequest> pendingTrips(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> TripRequest.builder().id((long) i + 1)
                        .status(TripRequestStatus.PENDING).build())
                .toList();
    }
}
