package com.fleetops.core.module.vehicle.repository;

import com.fleetops.core.module.vehicle.model.ServiceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceHistoryRepository extends JpaRepository<ServiceHistory, Long> {
    List<ServiceHistory> findByVehicleIdOrderByServicedAtDesc(Long vehicleId);
}
