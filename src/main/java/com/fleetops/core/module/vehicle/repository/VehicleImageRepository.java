package com.fleetops.core.module.vehicle.repository;

import com.fleetops.core.module.vehicle.model.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {
    List<VehicleImage> findByVehicleId(Long vehicleId);
    List<VehicleImage> findByVehicleIdIn(List<Long> vehicleIds);
    Optional<VehicleImage> findByImageId(String imageId);
}
