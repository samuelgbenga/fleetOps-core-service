package com.fleetops.core.module.company.repository;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.model.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByName(String name);
    List<Company> findAllByStatus(CompanyStatus status);
}
