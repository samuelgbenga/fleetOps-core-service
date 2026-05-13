package com.fleetops.core.mileage.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.kafka.event.VehicleActivityEvent;
import com.fleetops.core.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.mileage.dto.MileageLogRequest;
import com.fleetops.core.mileage.dto.MileageLogResponse;
import com.fleetops.core.mileage.entity.MileageLog;
import com.fleetops.core.mileage.repository.MileageLogRepository;
import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import com.fleetops.core.vehicle.service.VehicleLifecycleService;
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
    private final TripRequestRepository tripRequestRepository;
    private final MaintenanceEventProducer maintenanceEventProducer;
    private final VehicleActivityProducer vehicleActivityProducer;
    private final VehicleLifecycleService vehicleLifecycleService;

    @Transactional
    public MileageLogResponse submitLog(MileageLogRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User submittedBy = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + request.getVehicleId()));

        TripRequest linkedTrip = null;
        if (request.getTripRequestId() != null) {
            linkedTrip = tripRequestRepository.findById(request.getTripRequestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trip request not found: " + request.getTripRequestId()));
            if (!linkedTrip.getVehicle().getId().equals(vehicle.getId())) {
                throw new ConflictException("Trip request does not belong to vehicle " + vehicle.getPlateNumber());
            }
            if (!linkedTrip.getFieldStaff().getId().equals(submittedBy.getId())) {
                throw new ConflictException("Trip request does not belong to the submitting user");
            }
            if (linkedTrip.getStatus() != TripRequestStatus.COMPLETED) {
                throw new ConflictException("Mileage can only be logged against a completed trip");
            }
        } else {
            boolean hasCompletedTrip = tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(
                    submittedBy.getId(), vehicle.getId(), TripRequestStatus.COMPLETED);
            if (!hasCompletedTrip) {
                throw new ConflictException(
                        "Mileage can only be logged after a completed trip. " +
                        "No completed trip found for vehicle " + vehicle.getPlateNumber() +
                        " under your account.");
            }
        }

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
                .tripRequest(linkedTrip)
                .reportedMileage(reportedMileage)
                .build();
        mileageLogRepository.save(mileageLog);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("MILEAGE_SUBMITTED")
                .vehicleId(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .description(String.format("%s (%s) reported odometer reading of %.0f km on vehicle %s",
                        submittedBy.getName(), submittedBy.getRole().name(),
                        reportedMileage, vehicle.getPlateNumber()))
                .actorName(submittedBy.getName())
                .actorRole(submittedBy.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        if (vehicle.isMilestoneReached(previousMileage)) {
            log.info("Milestone reached for vehicle {}. Publishing maintenance event.", vehicle.getPlateNumber());
            publishMaintenanceEvent(vehicle, reportedMileage);
        }

        vehicleLifecycleService.recalculateForVehicle(vehicle.getId());

        return MileageLogResponse.from(mileageLog);
    }

    public List<MileageLogResponse> getLogsByVehicle(Long vehicleId) {
        vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        return mileageLogRepository.findByVehicleIdOrderByLoggedAtDesc(vehicleId)
                .stream().map(MileageLogResponse::from).toList();
    }

    private void publishMaintenanceEvent(Vehicle vehicle, Double mileageAtTrigger) {
        List<User> managers = userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER);
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
