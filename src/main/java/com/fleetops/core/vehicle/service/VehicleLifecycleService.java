package com.fleetops.core.vehicle.service;

import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.mileage.repository.MileageLogRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleLifecycleService {

    private final VehicleRepository vehicleRepository;
    private final MileageLogRepository mileageLogRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recalculateAll() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        vehicles.forEach(this::recalculate);
        vehicleRepository.saveAll(vehicles);
        log.info("Lifecycle recalculation complete for {} vehicles", vehicles.size());
    }

    @Transactional
    public void recalculateForVehicle(Long vehicleId) {
        vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            recalculate(vehicle);
            vehicleRepository.save(vehicle);
        });
    }

    private void recalculate(Vehicle vehicle) {
        double mileageFactor = Math.min(1.0, vehicle.getCurrentMileage() / vehicle.getMaxMileage());

        long qualifiedTrips = mileageLogRepository.countQualifiedTripsByVehicleId(vehicle.getId());
        double tripFactor = Math.min(1.0, (double) qualifiedTrips / vehicle.getMaxTrips());

        long resolvedMaintenance = maintenanceFlagRepository
                .countByVehicleIdAndStatus(vehicle.getId(), FlagStatus.RESOLVED);
        double maintenanceFactor = Math.min(1.0, (double) resolvedMaintenance / vehicle.getMaxMaintenanceRounds());

        double lifecycle = Math.min(100.0,
                (mileageFactor * 0.5 + tripFactor * 0.25 + maintenanceFactor * 0.25) * 100);

        vehicle.setLifecyclePercentage(lifecycle);

        if (lifecycle >= 80.0 && vehicle.getStatus() != VehicleStatus.OUT_OF_SERVICE) {
            vehicle.setStatus(VehicleStatus.OUT_OF_SERVICE);
            vehicle.setMarkedForSale(true);
            log.warn("Vehicle {} reached {}% lifecycle — marked OUT_OF_SERVICE and for sale",
                    vehicle.getPlateNumber(), String.format("%.1f", lifecycle));
        }
    }
}
