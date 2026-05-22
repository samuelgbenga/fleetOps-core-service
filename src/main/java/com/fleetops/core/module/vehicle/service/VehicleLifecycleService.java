package com.fleetops.core.module.vehicle.service;

public interface VehicleLifecycleService {
    void recomputeAll();
    void recomputeForVehicle(Long vehicleId);
}
