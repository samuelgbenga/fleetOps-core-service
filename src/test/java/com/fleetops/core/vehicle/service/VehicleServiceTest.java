package com.fleetops.core.vehicle.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.vehicle.dto.MilestoneIntervalRequest;
import com.fleetops.core.vehicle.dto.VehicleRequest;
import com.fleetops.core.vehicle.dto.VehicleResponse;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.ServiceHistoryRepository;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private ServiceHistoryRepository serviceHistoryRepository;

    @InjectMocks private VehicleService vehicleService;

    @BeforeEach
    void injectConfig() {
        ReflectionTestUtils.setField(vehicleService, "defaultMilestoneInterval", 3000.0);
    }

    // ── registerVehicle ──────────────────────────────────────────────────────

    @Test
    void registerVehicle_success_returnsResponse() {
        VehicleRequest req = new VehicleRequest();
        req.setMake("Toyota");
        req.setModel("Camry");
        req.setPlateNumber("ABC-123");
        req.setMilestoneInterval(5000.0);

        Vehicle saved = vehicle(1L, "Toyota", "Camry", "ABC-123", VehicleStatus.AVAILABLE);
        when(vehicleRepository.existsByPlateNumber("ABC-123")).thenReturn(false);
        when(vehicleRepository.save(any())).thenReturn(saved);

        VehicleResponse response = vehicleService.registerVehicle(req);

        assertThat(response.getPlateNumber()).isEqualTo("ABC-123");
        assertThat(response.getMake()).isEqualTo("Toyota");
        assertThat(response.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository).save(any());
    }

    @Test
    void registerVehicle_defaultMilestoneInterval_usedWhenNull() {
        VehicleRequest req = new VehicleRequest();
        req.setMake("Ford");
        req.setModel("Transit");
        req.setPlateNumber("XYZ-999");
        req.setMilestoneInterval(null);

        Vehicle saved = vehicle(2L, "Ford", "Transit", "XYZ-999", VehicleStatus.AVAILABLE);
        when(vehicleRepository.existsByPlateNumber("XYZ-999")).thenReturn(false);
        when(vehicleRepository.save(any())).thenReturn(saved);

        vehicleService.registerVehicle(req);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getMilestoneInterval()).isEqualTo(3000.0);
    }

    @Test
    void registerVehicle_customMilestoneInterval_respected() {
        VehicleRequest req = new VehicleRequest();
        req.setMake("Isuzu");
        req.setModel("NPR");
        req.setPlateNumber("LAG-500-AA");
        req.setMilestoneInterval(7500.0);

        Vehicle saved = vehicle(3L, "Isuzu", "NPR", "LAG-500-AA", VehicleStatus.AVAILABLE);
        when(vehicleRepository.existsByPlateNumber("LAG-500-AA")).thenReturn(false);
        when(vehicleRepository.save(any())).thenReturn(saved);

        vehicleService.registerVehicle(req);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getMilestoneInterval()).isEqualTo(7500.0);
    }

    @Test
    void registerVehicle_duplicatePlate_throwsConflict() {
        VehicleRequest req = new VehicleRequest();
        req.setPlateNumber("DUP-001");
        when(vehicleRepository.existsByPlateNumber("DUP-001")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.registerVehicle(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DUP-001");
        verify(vehicleRepository, never()).save(any());
    }

    // ── getAllVehicles ───────────────────────────────────────────────────────

    @Test
    void getAllVehicles_returnsAll() {
        when(vehicleRepository.findAll()).thenReturn(List.of(
                vehicle(1L, "Toyota", "Camry", "AAA-001", VehicleStatus.AVAILABLE),
                vehicle(2L, "Ford", "Ranger", "BBB-002", VehicleStatus.ASSIGNED)
        ));

        List<VehicleResponse> result = vehicleService.getAllVehicles();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(VehicleResponse::getPlateNumber)
                .containsExactly("AAA-001", "BBB-002");
    }

    @Test
    void getAllVehicles_emptyFleet_returnsEmpty() {
        when(vehicleRepository.findAll()).thenReturn(List.of());
        assertThat(vehicleService.getAllVehicles()).isEmpty();
    }

    @Test
    void getAllVehicles_serviceHistories_alwaysEmpty() {
        when(vehicleRepository.findAll()).thenReturn(List.of(
                vehicle(1L, "Toyota", "Camry", "AAA-001", VehicleStatus.AVAILABLE)
        ));

        List<VehicleResponse> result = vehicleService.getAllVehicles();

        assertThat(result.get(0).getServiceHistories()).isEmpty();
    }

    // ── getAvailableVehicles ─────────────────────────────────────────────────

    @Test
    void getAvailableVehicles_returnsOnlyAvailableStatus() {
        when(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)).thenReturn(List.of(
                vehicle(1L, "Toyota", "Camry", "AAA-001", VehicleStatus.AVAILABLE)
        ));

        List<VehicleResponse> result = vehicleService.getAvailableVehicles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    // ── getVehicleById ───────────────────────────────────────────────────────

    @Test
    void getVehicleById_found_returnsResponseWithServiceHistories() {
        Vehicle v = vehicle(1L, "Toyota", "Camry", "AAA-001", VehicleStatus.AVAILABLE);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(v));
        when(serviceHistoryRepository.findByVehicleIdOrderByServicedAtDesc(1L))
                .thenReturn(List.of());

        VehicleResponse response = vehicleService.getVehicleById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getPlateNumber()).isEqualTo("AAA-001");
        assertThat(response.getServiceHistories()).isEmpty();
    }

    @Test
    void getVehicleById_notFound_throwsResourceNotFound() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getVehicleById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── updateMilestoneInterval ──────────────────────────────────────────────

    @Test
    void updateMilestoneInterval_success_newIntervalTakesEffectImmediately() {
        Vehicle existing = vehicle(1L, "Toyota", "Camry", "AAA-001", VehicleStatus.AVAILABLE);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MilestoneIntervalRequest req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(6000.0);

        VehicleResponse response = vehicleService.updateMilestoneInterval(1L, req);

        assertThat(response.getMilestoneInterval()).isEqualTo(6000.0);
        verify(vehicleRepository).save(existing);
    }

    @Test
    void updateMilestoneInterval_vehicleNotFound_throwsResourceNotFound() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        MilestoneIntervalRequest req = new MilestoneIntervalRequest();
        req.setMilestoneInterval(3000.0);

        assertThatThrownBy(() -> vehicleService.updateMilestoneInterval(99L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Vehicle vehicle(Long id, String make, String model, String plate, VehicleStatus status) {
        return Vehicle.builder()
                .id(id).make(make).model(model).plateNumber(plate)
                .currentMileage(0.0).milestoneInterval(3000.0).status(status)
                .build();
    }
}
