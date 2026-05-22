package com.fleetops.core.module.triprequest.repository;

import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRequestRepository extends JpaRepository<TripRequest, Long> {
    List<TripRequest> findByCompanyId(Long companyId);
    List<TripRequest> findByCompanyIdAndStatus(Long companyId, TripRequestStatus status);
    List<TripRequest> findByRequestedByIdAndCompanyId(Long userId, Long companyId);
    Optional<TripRequest> findByIdAndCompanyId(Long id, Long companyId);
    List<TripRequest> findByVehicleIdAndStatus(Long vehicleId, TripRequestStatus status);

    @Query("SELECT t FROM TripRequest t WHERE t.status = 'PENDING' AND t.createdAt < :cutoff")
    List<TripRequest> findStalePendingRequests(LocalDateTime cutoff);

    long countByCompanyIdAndStatus(Long companyId, TripRequestStatus status);
    List<TripRequest> findByVehicleIdAndCompanyId(Long vehicleId, Long companyId);
    Optional<TripRequest> findByRequestedByIdAndVehicleIdAndStatus(Long userId, Long vehicleId, TripRequestStatus status);
    List<TripRequest> findByRequestedByIdAndStatusIn(Long userId, List<TripRequestStatus> statuses);
}
