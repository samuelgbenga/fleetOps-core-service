package com.fleetops.core.module.vehicle.service;

import com.fleetops.core.module.vehicle.dto.MilestoneIntervalRequest;
import com.fleetops.core.module.vehicle.dto.VehicleImageRequest;
import com.fleetops.core.module.vehicle.dto.VehicleImageResponse;
import com.fleetops.core.module.vehicle.dto.VehicleRequest;
import com.fleetops.core.module.vehicle.dto.VehicleResponse;
import com.fleetops.core.module.vehicle.model.Vehicle;

import java.util.List;

public interface VehicleService {
    VehicleResponse registerVehicle(VehicleRequest request);
    List<VehicleResponse> getAllVehicles();
    List<VehicleResponse> getAvailableVehicles();
    List<VehicleResponse> getMyTripVehicles();
    VehicleResponse getVehicleById(Long id);
    VehicleResponse updateMilestoneInterval(Long id, MilestoneIntervalRequest request);
    VehicleImageResponse uploadVehicleImage(Long vehicleId, VehicleImageRequest request);
    List<VehicleImageResponse> uploadBulkVehicleImages(Long vehicleId, List<VehicleImageRequest> requests);
    void deleteVehicleImage(Long vehicleId, String imageId);
    void recomputeHealthForVehicle(Vehicle vehicle);
}
