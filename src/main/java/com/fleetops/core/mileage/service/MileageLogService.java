package com.fleetops.core.mileage.service;

import com.fleetops.core.exception.ConflictException;
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

        Double reportedMileage = request.getReportedMileage();
        Double previousMileage = vehicle.getCurrentMileage();

        if (reportedMileage < previousMileage) {
            throw new ConflictException(
                    "Reported mileage (" + reportedMileage + " km) cannot be less than the vehicle's " +
                    "current recorded mileage (" + previousMileage + " km)"
            );
        }

        // Set odometer reading directly — this is not a per-trip delta
        vehicle.setCurrentMileage(reportedMileage);
        vehicleRepository.save(vehicle);

        MileageLog mileageLog = MileageLog.builder()
                .vehicle(vehicle)
                .submittedBy(submittedBy)
                .reportedMileage(reportedMileage)
                .build();
        mileageLogRepository.save(mileageLog);

        if (vehicle.isMilestoneReached(previousMileage)) {
            log.info("Milestone reached for vehicle {}. Publishing maintenance event.", vehicle.getPlateNumber());
            publishMaintenanceEvent(vehicle, reportedMileage);
        }

        return MileageLogResponse.from(mileageLog);
    }

    public List<MileageLogResponse> getLogsByVehicle(Long vehicleId) {
        vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        return mileageLogRepository.findByVehicleIdOrderByLoggedAtDesc(vehicleId)
                .stream().map(MileageLogResponse::from).toList();
    }

    private void publishMaintenanceEvent(Vehicle vehicle, Double mileageAtTrigger) {
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
