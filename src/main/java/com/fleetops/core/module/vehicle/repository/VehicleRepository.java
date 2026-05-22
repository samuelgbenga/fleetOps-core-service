package com.fleetops.core.module.vehicle.repository;

import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByCompanyId(Long companyId);
    List<Vehicle> findByCompanyIdAndStatus(Long companyId, VehicleStatus status);
    Optional<Vehicle> findByIdAndCompanyId(Long id, Long companyId);
    boolean existsByPlateNumber(String plateNumber);
    List<Vehicle> findByStatus(VehicleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdWithLock(Long id);
}
