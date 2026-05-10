package com.fleetops.core.vehicle.repository;

import com.fleetops.core.vehicle.entity.LgaCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LgaCodeRepository extends JpaRepository<LgaCode, Long> {
    boolean existsByCode(String code);
}
