package com.fleetops.core.mileage.service;

import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.mileage.dto.MileageLogRequest;
import com.fleetops.core.mileage.dto.MileageLogResponse;
import com.fleetops.core.mileage.entity.MileageLog;
import com.fleetops.core.mileage.repository.MileageLogRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MileageLogService {

    private final MileageLogRepository mileageLogRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final MaintenanceEventProducer maintenanceEventProducer;

    @Transactional
    public MileageLogResponse submitLog(MileageLogRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User submittedBy = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + request.getVehicleId()));

        Double oldMileage = vehicle.getCurrentMileage();
        Double newMileage = oldMileage + request.getMileageAdded();

        // Update vehicle mileage
        vehicle.setCurrentMileage(newMileage);
        vehicleRepository.save(vehicle);

        // Save mileage log
        MileageLog log = MileageLog.builder()
                .vehicle(vehicle)
                .submittedBy(submittedBy)
                .mileageAdded(request.getMileageAdded())
                .build();
        mileageLogRepository.save(log);

        // Check if milestone has been crossed
        if (vehicle.isMilestoneReached(oldMileage)) {
            log.info("Milestone reached for vehicle {}. Publishing event.", vehicle.getPlateNumber());
            publishMaintenanceEvent(vehicle, newMileage);
        }

        return MileageLogResponse.from(log, newMileage);
    }

    private void publishMaintenanceEvent(Vehicle vehicle, Double mileageAtTrigger) {
        // Find a fleet manager to notify (pick the first one found)
        List<User> managers = userRepository.findByRole(UserRole.FLEET_MANAGER);
        if (managers.isEmpty()) {
            log.warn("No FLEET_MANAGER found to notify for vehicle {}", vehicle.getPlateNumber());
            return;
        }
        User manager = managers.get(0);

        MaintenanceFlagCreatedEvent event = MaintenanceFlagCreatedEvent.builder()
                .vehicleId(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .mileageAtTrigger(mileageAtTrigger)
                .fleetManagerId(manager.getId())
                .fleetManagerEmail(manager.getEmail())
                .fleetManagerName(manager.getName())
                .occurredAt(LocalDateTime.now())
                .build();

        maintenanceEventProducer.publish(event);
    }
}
