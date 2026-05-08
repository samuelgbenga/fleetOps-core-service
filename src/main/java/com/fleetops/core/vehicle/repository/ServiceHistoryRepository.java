package com.fleetops.core.vehicle.repository;

import com.fleetops.core.vehicle.entity.ServiceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceHistoryRepository extends JpaRepository<ServiceHistory, Long> {
    List<ServiceHistory> findByVehicleIdOrderByServicedAtDesc(Long vehicleId);
}
