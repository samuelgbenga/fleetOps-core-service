package com.fleetops.core.module.triprequest.repository;

import com.fleetops.core.module.triprequest.model.VehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, Long> {
    List<VehicleAssignment> findByCompanyId(Long companyId);
    List<VehicleAssignment> findByVehicleId(Long vehicleId);
}
