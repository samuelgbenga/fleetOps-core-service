package com.fleetops.core.module.maintenance.repository;

import com.fleetops.core.module.maintenance.model.MaintenanceQuotation;
import com.fleetops.core.module.maintenance.model.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceQuotationRepository extends JpaRepository<MaintenanceQuotation, Long> {
    List<MaintenanceQuotation> findByFlagId(Long flagId);
    Optional<MaintenanceQuotation> findByFlagIdAndStatus(Long flagId, QuotationStatus status);
    Optional<MaintenanceQuotation> findTopByFlagIdOrderByRevisionNumberDesc(Long flagId);
}
