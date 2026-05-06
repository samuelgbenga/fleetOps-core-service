package com.fleetops.core.maintenance.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.maintenance.dto.AssignFlagRequest;
import com.fleetops.core.maintenance.dto.MaintenanceFlagResponse;
import com.fleetops.core.maintenance.dto.ProgressUpdateRequest;
import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.enums.VehicleStatus;
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

        // Notify maintenance team member
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

        // Notify fleet manager
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

    @Transactional
    public MaintenanceFlagResponse resolveFlag(Long flagId) {
        MaintenanceFlag flag = getFlagOrThrow(flagId);

        if (flag.getStatus() == FlagStatus.RESOLVED) {
            throw new ConflictException("Flag is already resolved");
        }

        flag.setStatus(FlagStatus.RESOLVED);
        flag.setResolvedAt(LocalDateTime.now());
        maintenanceFlagRepository.save(flag);

        // Set vehicle back to AVAILABLE
        flag.getVehicle().setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(flag.getVehicle());

        // Notify fleet manager
        if (flag.getAssignedBy() != null) {
            publishNotification(
                    flag.getAssignedBy().getEmail(),
                    flag.getAssignedBy().getName(),
                    "Maintenance Resolved: Vehicle " + flag.getVehicle().getPlateNumber(),
                    String.format("Hi %s,\n\nMaintenance for vehicle %s has been completed.\n" +
                            "The vehicle is now AVAILABLE for new trip requests.\n\nFleetOps System",
                            flag.getAssignedBy().getName(),
                            flag.getVehicle().getPlateNumber()),
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
