package com.fleetops.core.assignment.repository;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, Long> {

    List<VehicleAssignment> findByVehicleId(Long vehicleId);

    @Query("""
            SELECT COUNT(a) > 0 FROM VehicleAssignment a
            WHERE a.vehicle.id = :vehicleId
            AND a.startDate < :endDate
            AND a.endDate > :startDate
            """)
    boolean existsOverlappingAssignment(
            @Param("vehicleId") Long vehicleId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
