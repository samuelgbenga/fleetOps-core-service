package com.fleetops.core.module.lga.repository;

import com.fleetops.core.module.lga.model.LgaCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LgaCodeRepository extends JpaRepository<LgaCode, String> {
    boolean existsByCode(String code);
}
