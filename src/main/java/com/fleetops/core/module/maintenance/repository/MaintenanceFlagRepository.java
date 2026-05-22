package com.fleetops.core.module.maintenance.repository;

import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceFlagRepository extends JpaRepository<MaintenanceFlag, Long> {
    List<MaintenanceFlag> findByCompanyId(Long companyId);
    List<MaintenanceFlag> findByCompanyIdAndStatus(Long companyId, FlagStatus status);
    List<MaintenanceFlag> findByVehicleIdAndCompanyId(Long vehicleId, Long companyId);
    List<MaintenanceFlag> findByAssignedCrewId(Long crewId);
    Optional<MaintenanceFlag> findByIdAndCompanyId(Long id, Long companyId);
    long countByVehicleIdAndStatus(Long vehicleId, FlagStatus status);
    List<MaintenanceFlag> findByStatus(FlagStatus status);
}
