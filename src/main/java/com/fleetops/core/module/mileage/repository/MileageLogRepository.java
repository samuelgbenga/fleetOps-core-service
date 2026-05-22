package com.fleetops.core.module.mileage.repository;

import com.fleetops.core.module.mileage.model.MileageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MileageLogRepository extends JpaRepository<MileageLog, Long> {
    List<MileageLog> findByVehicleIdAndCompanyId(Long vehicleId, Long companyId);

    @Query("SELECT COUNT(m) FROM MileageLog m WHERE m.vehicle.id = :vehicleId AND m.tripRequest IS NOT NULL")
    long countQualifiedTripsByVehicleId(Long vehicleId);
}
