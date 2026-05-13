package com.fleetops.core.mileage.repository;

import com.fleetops.core.mileage.entity.MileageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MileageLogRepository extends JpaRepository<MileageLog, Long> {
    List<MileageLog> findByVehicleIdOrderByLoggedAtDesc(Long vehicleId);

    @Query("SELECT COUNT(DISTINCT ml.tripRequest.id) FROM MileageLog ml " +
           "WHERE ml.vehicle.id = :vehicleId AND ml.tripRequest IS NOT NULL")
    long countQualifiedTripsByVehicleId(@Param("vehicleId") Long vehicleId);
}
