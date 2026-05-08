package com.fleetops.core.maintenance.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.maintenance.dto.ApproveFlagRequest;
import com.fleetops.core.maintenance.dto.AssignFlagRequest;
import com.fleetops.core.maintenance.dto.MaintenanceFlagResponse;
import com.fleetops.core.maintenance.dto.ProgressUpdateRequest;
import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.ServiceHistory;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.ServiceHistoryRepository;
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
public class MaintenanceFlagService {

    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceHistoryRepository serviceHistoryRepository;
    private final NotificationEventProducer notificationEventProducer;

    public List<MaintenanceFlagResponse> getAllFlags() {
        return maintenanceFlagRepository.findAll()
                .stream().map(MaintenanceFlagResponse::from).toList();
    }

    public List<MaintenanceFlagResponse> getMyAssignedFlags() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return maintenanceFlagRepository.findByAssignedToId(user.getId())
                .stream().map(MaintenanceFlagResponse::from).toList();
    }

    @Transactional
    public MaintenanceFlagResponse assignFlag(Long flagId, AssignFlagRequest request) {
        MaintenanceFlag flag = getFlagOrThrow(flagId);

        if (flag.getStatus() != FlagStatus.OPEN) {
            throw new ConflictException("Flag is not in OPEN status");
        }

        User maintenanceUser = userRepository.findById(request.getMaintenanceTeamUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance team user not found: " + request.getMaintenanceTeamUserId()));

        String managerEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));

        flag.setAssignedTo(maintenanceUser);
        flag.setAssignedBy(manager);
        flag.setStatus(FlagStatus.ASSIGNED);
        flag.setAssignedAt(LocalDateTime.now());
        maintenanceFlagRepository.save(flag);

        publishNotification(
                maintenanceUser.getEmail(),
                maintenanceUser.getName(),
                "Maintenance Task Assigned: Vehicle " + flag.getVehicle().getPlateNumber(),
                String.format("Hi %s,\n\nYou have been assigned a maintenance task for vehicle %s.\n" +
                        "Mileage at trigger: %.0f km\n\nPlease update your progress once you begin.\n\nFleetOps System",
                        maintenanceUser.getName(),
                        flag.getVehicle().getPlateNumber(),
                        flag.getMileageAtTrigger()),
                "FLAG_ASSIGNED"
        );

        return MaintenanceFlagResponse.from(flag);
    }

    @Transactional
    public MaintenanceFlagResponse updateProgress(Long flagId, ProgressUpdateRequest request) {
        MaintenanceFlag flag = getFlagOrThrow(flagId);

        if (flag.getStatus() != FlagStatus.ASSIGNED && flag.getStatus() != FlagStatus.IN_PROGRESS) {
            throw new ConflictException("Flag must be ASSIGNED or IN_PROGRESS to update progress");
        }

        flag.setProgressNotes(request.getProgressNotes());
        flag.setStatus(FlagStatus.IN_PROGRESS);
        maintenanceFlagRepository.save(flag);

        if (flag.getAssignedBy() != null) {
            publishNotification(
                    flag.getAssignedBy().getEmail(),
                    flag.getAssignedBy().getName(),
                    "Maintenance Progress Update: Vehicle " + flag.getVehicle().getPlateNumber(),
                    String.format("Hi %s,\n\nProgress update for vehicle %s:\n\n%s\n\nFleetOps System",
                            flag.getAssignedBy().getName(),
                            flag.getVehicle().getPlateNumber(),
                            request.getProgressNotes()),
                    "FLAG_PROGRESS"
            );
        }

        return MaintenanceFlagResponse.from(flag);
    }

    /**
     * Maintenance team signals work is done. Moves flag to PENDING_APPROVAL
     * and notifies the fleet manager to review and approve.
     */
    @Transactional
    public MaintenanceFlagResponse markWorkDone(Long flagId) {
        MaintenanceFlag flag = getFlagOrThrow(flagId);

        if (flag.getStatus() != FlagStatus.ASSIGNED && flag.getStatus() != FlagStatus.IN_PROGRESS) {
            throw new ConflictException("Flag must be ASSIGNED or IN_PROGRESS to mark as done");
        }

        flag.setStatus(FlagStatus.PENDING_APPROVAL);
        flag.setPendingApprovalAt(LocalDateTime.now());
        maintenanceFlagRepository.save(flag);

        if (flag.getAssignedBy() != null) {
            String assigneeName = flag.getAssignedTo() != null ? flag.getAssignedTo().getName() : "Maintenance team";
            publishNotification(
                    flag.getAssignedBy().getEmail(),
                    flag.getAssignedBy().getName(),
                    "Maintenance Completed — Approval Required: Vehicle " + flag.getVehicle().getPlateNumber(),
                    String.format("Hi %s,\n\n%s has completed maintenance on vehicle %s and is awaiting your approval.\n\n" +
                            "Please review and approve the maintenance to return the vehicle to service.\n\nFleetOps System",
                            flag.getAssignedBy().getName(),
                            assigneeName,
                            flag.getVehicle().getPlateNumber()),
                    "FLAG_PENDING_APPROVAL"
            );
        }

        return MaintenanceFlagResponse.from(flag);
    }

    /**
     * Fleet manager approves maintenance completion. Requires a new milestone interval
     * (greater than both the current interval and the vehicle's current mileage)
     * and service notes. Creates a ServiceHistory record and returns the vehicle to AVAILABLE.
     */
    @Transactional
    public MaintenanceFlagResponse approveMaintenance(Long flagId, ApproveFlagRequest request) {
        MaintenanceFlag flag = getFlagOrThrow(flagId);

        if (flag.getStatus() != FlagStatus.PENDING_APPROVAL) {
            throw new ConflictException("Flag must be in PENDING_APPROVAL status to approve");
        }

        Vehicle vehicle = flag.getVehicle();

        if (request.getNewMilestoneInterval() <= vehicle.getMilestoneInterval()) {
            throw new ConflictException(
                    "New milestone interval (" + request.getNewMilestoneInterval() + " km) must be greater " +
                    "than the previous interval (" + vehicle.getMilestoneInterval() + " km)"
            );
        }
        if (request.getNewMilestoneInterval() <= vehicle.getCurrentMileage()) {
            throw new ConflictException(
                    "New milestone interval (" + request.getNewMilestoneInterval() + " km) must be greater " +
                    "than the vehicle's current mileage (" + vehicle.getCurrentMileage() + " km)"
            );
        }

        String managerEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));

        // Record the service history
        ServiceHistory history = ServiceHistory.builder()
                .vehicle(vehicle)
                .fleetManagerName(manager.getName())
                .notes(request.getServiceNotes())
                .newMilestoneInterval(request.getNewMilestoneInterval())
                .build();
        serviceHistoryRepository.save(history);

        // Update vehicle: new milestone interval + restore to AVAILABLE
        vehicle.setMilestoneInterval(request.getNewMilestoneInterval());
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(vehicle);

        // Close the flag
        flag.setStatus(FlagStatus.RESOLVED);
        flag.setResolvedAt(LocalDateTime.now());
        maintenanceFlagRepository.save(flag);

        // Notify maintenance team member that approval went through
        if (flag.getAssignedTo() != null) {
            publishNotification(
                    flag.getAssignedTo().getEmail(),
                    flag.getAssignedTo().getName(),
                    "Maintenance Approved: Vehicle " + vehicle.getPlateNumber(),
                    String.format("Hi %s,\n\nYour maintenance work on vehicle %s has been approved by %s.\n" +
                            "The vehicle has been returned to service.\n\nFleetOps System",
                            flag.getAssignedTo().getName(),
                            vehicle.getPlateNumber(),
                            manager.getName()),
                    "FLAG_RESOLVED"
            );
        }

        return MaintenanceFlagResponse.from(flag);
    }

    private void publishNotification(String email, String name, String subject,
                                     String message, String type) {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipientEmail(email)
                .recipientName(name)
                .subject(subject)
                .message(message)
                .type(type)
                .occurredAt(LocalDateTime.now())
                .build();
        notificationEventProducer.publish(event);
    }

    private MaintenanceFlag getFlagOrThrow(Long id) {
        return maintenanceFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance flag not found: " + id));
    }
}
