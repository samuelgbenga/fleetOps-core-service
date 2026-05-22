package com.fleetops.core.module.maintenance.repository;

import com.fleetops.core.module.maintenance.model.MaintenanceMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceMessageRepository extends JpaRepository<MaintenanceMessage, Long> {
    List<MaintenanceMessage> findByFlagIdOrderBySentAtAsc(Long flagId);
}
