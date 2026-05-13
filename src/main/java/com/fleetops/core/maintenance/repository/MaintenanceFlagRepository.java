package com.fleetops.core.maintenance.repository;

import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.enums.FlagStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceFlagRepository extends JpaRepository<MaintenanceFlag, Long> {
    List<MaintenanceFlag> findByStatus(FlagStatus status);
    List<MaintenanceFlag> findByVehicleId(Long vehicleId);
    List<MaintenanceFlag> findByAssignedToId(Long userId);
    long countByVehicleIdAndStatus(Long vehicleId, FlagStatus status);
}
