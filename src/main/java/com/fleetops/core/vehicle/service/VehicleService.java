package com.fleetops.core.vehicle.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.vehicle.dto.MilestoneIntervalRequest;
import com.fleetops.core.vehicle.dto.ServiceHistoryResponse;
import com.fleetops.core.vehicle.dto.VehicleRequest;
import com.fleetops.core.vehicle.dto.VehicleResponse;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.ServiceHistoryRepository;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ServiceHistoryRepository serviceHistoryRepository;

    @Value("${app.vehicle.default-milestone-interval}")
    private Double defaultMilestoneInterval;

    public VehicleResponse registerVehicle(VehicleRequest request) {
        if (vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw new ConflictException("Plate number already registered: " + request.getPlateNumber());
        }
        Vehicle vehicle = Vehicle.builder()
                .make(request.getMake())
                .model(request.getModel())
                .plateNumber(request.getPlateNumber())
                .milestoneInterval(request.getMilestoneInterval() != null
                        ? request.getMilestoneInterval() : defaultMilestoneInterval)
                .build();
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll().stream().map(VehicleResponse::from).toList();
    }

    public List<VehicleResponse> getAvailableVehicles() {
        return vehicleRepository.findByStatus(VehicleStatus.AVAILABLE)
                .stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        List<ServiceHistoryResponse> histories = serviceHistoryRepository
                .findByVehicleIdOrderByServicedAtDesc(id)
                .stream().map(ServiceHistoryResponse::from).toList();
        return VehicleResponse.from(vehicle, histories);
    }

    @Transactional
    public VehicleResponse updateMilestoneInterval(Long id, MilestoneIntervalRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        vehicle.setMilestoneInterval(request.getMilestoneInterval());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }
}
