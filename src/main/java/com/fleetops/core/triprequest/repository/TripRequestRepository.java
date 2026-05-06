package com.fleetops.core.triprequest.repository;

import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripRequestRepository extends JpaRepository<TripRequest, Long> {
    List<TripRequest> findByStatus(TripRequestStatus status);
    List<TripRequest> findByFieldStaffId(Long fieldStaffId);
}
