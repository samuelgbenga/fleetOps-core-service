package com.fleetops.core.module.breakdown.repository;

import com.fleetops.core.module.breakdown.model.BreakdownReport;
import com.fleetops.core.module.breakdown.model.BreakdownStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BreakdownRepository extends JpaRepository<BreakdownReport, Long> {
    List<BreakdownReport> findByCompanyId(Long companyId);
    List<BreakdownReport> findByStatus(BreakdownStatus status);
    Optional<BreakdownReport> findByIdAndCompanyId(Long id, Long companyId);
    long countByVehicleId(Long vehicleId);
    List<BreakdownReport> findByVehicleIdAndCompanyId(Long vehicleId, Long companyId);
}
