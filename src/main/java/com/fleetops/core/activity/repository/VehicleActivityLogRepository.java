package com.fleetops.core.activity.repository;

import com.fleetops.core.activity.entity.VehicleActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VehicleActivityLogRepository extends JpaRepository<VehicleActivityLog, Long> {

    List<VehicleActivityLog> findAllByOrderByOccurredAtDesc();

    List<VehicleActivityLog> findByPlateNumberOrderByOccurredAtDesc(String plateNumber);

    List<VehicleActivityLog> findByOccurredAtBetweenOrderByOccurredAtDesc(
            LocalDateTime start, LocalDateTime end);

    List<VehicleActivityLog> findByPlateNumberAndOccurredAtBetweenOrderByOccurredAtDesc(
            String plateNumber, LocalDateTime start, LocalDateTime end);
}
