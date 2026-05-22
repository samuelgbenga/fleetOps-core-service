package com.fleetops.core.module.vehicle.service;

public interface VehicleReportService {
    byte[] generateVehiclePdf(Long vehicleId);
}
