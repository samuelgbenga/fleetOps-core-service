package com.fleetops.core.module.maintenance.repository;

import com.fleetops.core.module.maintenance.model.MaintenanceRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MaintenanceRatingRepository extends JpaRepository<MaintenanceRating, Long> {
    List<MaintenanceRating> findByCrewId(Long crewId);
    boolean existsByFlagIdAndRatedByUserId(Long flagId, Long userId);

    @Query("SELECT AVG(r.stars) FROM MaintenanceRating r WHERE r.crew.id = :crewId")
    Double computeAverageRatingForCrew(Long crewId);

    @Query("SELECT COUNT(r) FROM MaintenanceRating r WHERE r.crew.id = :crewId")
    long countByCrewId(Long crewId);
}
