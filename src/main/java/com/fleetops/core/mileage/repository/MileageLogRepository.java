package com.fleetops.core.mileage.repository;

import com.fleetops.core.mileage.entity.MileageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MileageLogRepository extends JpaRepository<MileageLog, Long> {
    List<MileageLog> findByVehicleIdOrderByLoggedAtDesc(Long vehicleId);
}
