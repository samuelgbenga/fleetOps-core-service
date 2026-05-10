package com.fleetops.core.maintenance.repository;

import com.fleetops.core.maintenance.entity.MaintenanceMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceMessageRepository extends JpaRepository<MaintenanceMessage, Long> {
    List<MaintenanceMessage> findByFlagIdOrderBySentAtAsc(Long flagId);
}
