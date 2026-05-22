package com.fleetops.core.module.activity.repository;

import com.fleetops.core.module.activity.model.VehicleActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleActivityLogRepository extends JpaRepository<VehicleActivityLog, Long> {
    Page<VehicleActivityLog> findByCompanyId(Long companyId, Pageable pageable);
    Page<VehicleActivityLog> findAll(Pageable pageable);
    List<VehicleActivityLog> findByVehicleIdAndCompanyIdOrderByOccurredAtAsc(Long vehicleId, Long companyId);
}
