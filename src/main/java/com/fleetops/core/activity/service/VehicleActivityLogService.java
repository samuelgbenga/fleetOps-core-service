package com.fleetops.core.activity.service;

import com.fleetops.core.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.activity.repository.VehicleActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleActivityLogService {

    private final VehicleActivityLogRepository repository;

    public List<VehicleActivityLogResponse> search(String plateNumber, LocalDate date) {
        if (plateNumber != null && date != null) {
            return repository.findByPlateNumberAndOccurredAtBetweenOrderByOccurredAtDesc(
                            plateNumber,
                            date.atStartOfDay(),
                            date.atTime(LocalTime.MAX))
                    .stream().map(VehicleActivityLogResponse::from).toList();
        }
        if (plateNumber != null) {
            return repository.findByPlateNumberOrderByOccurredAtDesc(plateNumber)
                    .stream().map(VehicleActivityLogResponse::from).toList();
        }
        if (date != null) {
            return repository.findByOccurredAtBetweenOrderByOccurredAtDesc(
                            date.atStartOfDay(), date.atTime(LocalTime.MAX))
                    .stream().map(VehicleActivityLogResponse::from).toList();
        }
        return repository.findAllByOrderByOccurredAtDesc()
                .stream().map(VehicleActivityLogResponse::from).toList();
    }
}
