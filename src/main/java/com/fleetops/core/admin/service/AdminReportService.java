package com.fleetops.core.admin.service;

import com.fleetops.core.admin.dto.UtilisationReportResponse;
import com.fleetops.core.admin.dto.VehicleHealthResponse;
import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final VehicleRepository vehicleRepository;
    private final TripRequestRepository tripRequestRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;

    public UtilisationReportResponse getUtilisationReport() {
        return UtilisationReportResponse.builder()
                .totalVehicles(vehicleRepository.count())
                .availableVehicles(vehicleRepository.findByStatus(VehicleStatus.AVAILABLE).size())
                .assignedVehicles(vehicleRepository.findByStatus(VehicleStatus.ASSIGNED).size())
                .maintenanceVehicles(vehicleRepository.findByStatus(VehicleStatus.MAINTENANCE).size())
                .totalTripsAllTime(tripRequestRepository.count())
                .pendingTripRequests(tripRequestRepository.findByStatus(TripRequestStatus.PENDING).size())
                .build();
    }

    public List<VehicleHealthResponse> getVehicleHealthSummary() {
        return vehicleRepository.findAll().stream().map(vehicle -> {
            long openFlags = maintenanceFlagRepository.findByVehicleId(vehicle.getId())
                    .stream()
                    .filter(f -> f.getStatus() != FlagStatus.RESOLVED)
                    .count();

            return VehicleHealthResponse.builder()
                    .vehicleId(vehicle.getId())
                    .plateNumber(vehicle.getPlateNumber())
                    .make(vehicle.getMake())
                    .model(vehicle.getModel())
                    .currentMileage(vehicle.getCurrentMileage())
                    .milestoneInterval(vehicle.getMilestoneInterval())
                    .status(vehicle.getStatus())
                    .openMaintenanceFlags(openFlags)
                    .build();
        }).toList();
    }
}
