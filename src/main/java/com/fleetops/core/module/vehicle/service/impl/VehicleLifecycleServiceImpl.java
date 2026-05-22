package com.fleetops.core.module.vehicle.service.impl;

import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleLifecycleService;
import com.fleetops.core.module.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleLifecycleServiceImpl implements VehicleLifecycleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleService vehicleService;

    @Override
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recomputeAll() {
        long start = System.currentTimeMillis();
        List<Vehicle> vehicles = vehicleRepository.findAll();
        vehicles.forEach(vehicleService::recomputeHealthForVehicle);
        vehicleRepository.saveAll(vehicles);
        log.info("Vehicle health recomputation complete: count={} duration={}ms",
                vehicles.size(), System.currentTimeMillis() - start);
    }

    @Override
    @Transactional
    public void recomputeForVehicle(Long vehicleId) {
        vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            vehicleService.recomputeHealthForVehicle(vehicle);
            vehicleRepository.save(vehicle);
        });
    }
}
