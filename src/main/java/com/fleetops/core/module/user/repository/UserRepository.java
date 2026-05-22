package com.fleetops.core.module.user.repository;

import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByCompanyId(Long companyId);
    List<User> findByRole(Role role);
    List<User> findByRoleAndAvailableTrue(Role role);
    List<User> findByRoleAndCompanyId(Role role, Long companyId);
    List<User> findByRoleAndCompanyIdAndAvailableTrue(Role role, Long companyId);
}
