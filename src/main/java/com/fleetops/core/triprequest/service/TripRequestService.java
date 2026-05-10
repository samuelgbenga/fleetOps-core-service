package com.fleetops.core.triprequest.service;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.exception.VehicleNotAvailableException;
import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.event.VehicleActivityEvent;
import com.fleetops.core.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.mileage.entity.MileageLog;
import com.fleetops.core.mileage.repository.MileageLogRepository;
import com.fleetops.core.triprequest.dto.CompleteTripRequest;
import com.fleetops.core.triprequest.dto.TripRequestCreate;
import com.fleetops.core.triprequest.dto.TripRequestResponse;
import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripRequestService {

    private final TripRequestRepository tripRequestRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final UserRepository userRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final MileageLogRepository mileageLogRepository;
    private final MaintenanceEventProducer maintenanceEventProducer;
    private final VehicleActivityProducer vehicleActivityProducer;

    @Transactional
    public TripRequestResponse createRequest(TripRequestCreate dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User fieldStaff = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Vehicle vehicle = vehicleRepository.findById(dto.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + dto.getVehicleId()));

        if (!vehicle.isAvailable()) {
            throw new VehicleNotAvailableException(
                    "Vehicle " + vehicle.getPlateNumber() + " is currently " + vehicle.getStatus()
            );
        }

        boolean alreadyRequested = tripRequestRepository.existsByFieldStaffIdAndVehicleIdAndStatus(
                fieldStaff.getId(), vehicle.getId(), TripRequestStatus.PENDING
        );
        if (alreadyRequested) {
            throw new ConflictException(
                    "You already have a pending request for vehicle " + vehicle.getPlateNumber()
            );
        }

        boolean hasConflict = vehicleAssignmentRepository.existsOverlappingAssignment(
                vehicle.getId(), dto.getStartDate(), dto.getEndDate()
        );
        if (hasConflict) {
            throw new ConflictException(
                    "Vehicle " + vehicle.getPlateNumber() + " is already assigned for this period"
            );
        }

        TripRequest tripRequest = TripRequest.builder()
                .fieldStaff(fieldStaff)
                .vehicle(vehicle)
                .destination(dto.getDestination())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .build();

        TripRequest saved = tripRequestRepository.save(tripRequest);

        notifyFleetManagersOfNewRequest(fieldStaff, vehicle, dto);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("TRIP_REQUESTED")
                .vehicleId(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .description(String.format("%s (%s) requested vehicle %s for %s → destination: %s (%s – %s)",
                        fieldStaff.getName(), fieldStaff.getRole().name(),
                        vehicle.getPlateNumber(), vehicle.getMake() + " " + vehicle.getModel(),
                        dto.getDestination(), dto.getStartDate(), dto.getEndDate()))
                .actorName(fieldStaff.getName())
                .actorRole(fieldStaff.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(saved);
    }

    @Transactional
    public TripRequestResponse approveRequest(Long id) {
        TripRequest request = getRequestOrThrow(id);

        if (request.getStatus() != TripRequestStatus.PENDING) {
            throw new ConflictException("Trip request is not in PENDING status");
        }

        request.setStatus(TripRequestStatus.APPROVED);
        tripRequestRepository.save(request);

        VehicleAssignment assignment = VehicleAssignment.builder()
                .tripRequest(request)
                .vehicle(request.getVehicle())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        vehicleAssignmentRepository.save(assignment);

        request.getVehicle().setStatus(VehicleStatus.ASSIGNED);
        vehicleRepository.save(request.getVehicle());

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(request.getFieldStaff().getEmail())
                .recipientName(request.getFieldStaff().getName())
                .subject("Trip Request Approved")
                .message("Your trip to " + request.getDestination() + " ("
                        + request.getStartDate() + " – " + request.getEndDate()
                        + ") has been approved. Vehicle: " + request.getVehicle().getPlateNumber())
                .type("TRIP_APPROVED")
                .occurredAt(LocalDateTime.now())
                .build());

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("TRIP_APPROVED")
                .vehicleId(request.getVehicle().getId())
                .plateNumber(request.getVehicle().getPlateNumber())
                .description(String.format("Trip request #%d approved for vehicle %s — assigned to %s (destination: %s, %s – %s)",
                        request.getId(), request.getVehicle().getPlateNumber(),
                        request.getFieldStaff().getName(), request.getDestination(),
                        request.getStartDate(), request.getEndDate()))
                .actorName("Fleet Manager")
                .actorRole("FLEET_MANAGER")
                .occurredAt(LocalDateTime.now())
                .build());

        rejectConflictingPendingRequests(request);

        return TripRequestResponse.from(request);
    }

    private void rejectConflictingPendingRequests(TripRequest approved) {
        tripRequestRepository
                .findByVehicleIdAndStatus(approved.getVehicle().getId(), TripRequestStatus.PENDING)
                .stream()
                .filter(pending -> !pending.getId().equals(approved.getId()))
                .filter(pending -> approved.getEndDate().isAfter(pending.getStartDate()))
                .forEach(pending -> {
                    pending.setStatus(TripRequestStatus.REJECTED);
                    tripRequestRepository.save(pending);

                    notificationEventProducer.publish(NotificationRequestEvent.builder()
                            .recipientEmail(pending.getFieldStaff().getEmail())
                            .recipientName(pending.getFieldStaff().getName())
                            .subject("Trip Request Rejected")
                            .message("Your trip request to " + pending.getDestination() + " ("
                                    + pending.getStartDate() + " – " + pending.getEndDate()
                                    + ") was rejected because vehicle "
                                    + approved.getVehicle().getPlateNumber()
                                    + " has been assigned to another trip until "
                                    + approved.getEndDate() + ".")
                            .type("TRIP_REJECTED")
                            .occurredAt(LocalDateTime.now())
                            .build());

                    vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                            .eventType("TRIP_REJECTED")
                            .vehicleId(pending.getVehicle().getId())
                            .plateNumber(pending.getVehicle().getPlateNumber())
                            .description(String.format(
                                    "Trip request #%d auto-rejected for vehicle %s — submitted by %s; " +
                                    "vehicle already assigned to another trip until %s",
                                    pending.getId(), pending.getVehicle().getPlateNumber(),
                                    pending.getFieldStaff().getName(), approved.getEndDate()))
                            .actorName("System")
                            .actorRole("SYSTEM")
                            .occurredAt(LocalDateTime.now())
                            .build());
                });
    }

    @Transactional
    public TripRequestResponse rejectRequest(Long id) {
        TripRequest request = getRequestOrThrow(id);

        if (request.getStatus() != TripRequestStatus.PENDING) {
            throw new ConflictException("Trip request is not in PENDING status");
        }

        request.setStatus(TripRequestStatus.REJECTED);
        tripRequestRepository.save(request);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(request.getFieldStaff().getEmail())
                .recipientName(request.getFieldStaff().getName())
                .subject("Trip Request Rejected")
                .message("Your trip request to " + request.getDestination() + " ("
                        + request.getStartDate() + " – " + request.getEndDate()
                        + ") has been rejected.")
                .type("TRIP_REJECTED")
                .occurredAt(LocalDateTime.now())
                .build());

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("TRIP_REJECTED")
                .vehicleId(request.getVehicle().getId())
                .plateNumber(request.getVehicle().getPlateNumber())
                .description(String.format("Trip request #%d rejected for vehicle %s — submitted by %s (destination: %s)",
                        request.getId(), request.getVehicle().getPlateNumber(),
                        request.getFieldStaff().getName(), request.getDestination()))
                .actorName("Fleet Manager")
                .actorRole("FLEET_MANAGER")
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(request);
    }

    @Transactional
    public TripRequestResponse completeTrip(Long id, CompleteTripRequest body) {
        TripRequest request = getRequestOrThrow(id);

        if (request.getStatus() != TripRequestStatus.APPROVED) {
            throw new ConflictException("Only APPROVED trips can be marked as completed");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (caller.getRole() == UserRole.FIELD_STAFF
                && !request.getFieldStaff().getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can only complete your own trips");
        }

        request.setStatus(TripRequestStatus.COMPLETED);
        tripRequestRepository.save(request);

        Vehicle vehicle = request.getVehicle();
        vehicle.setStatus(VehicleStatus.AVAILABLE);

        if (body != null && body.getReportedMileage() != null) {
            Double reportedMileage = body.getReportedMileage();
            Double previousMileage = vehicle.getCurrentMileage();

            if (reportedMileage < previousMileage) {
                throw new ConflictException(
                        "Reported mileage (" + reportedMileage + " km) cannot be less than the vehicle's " +
                        "current recorded mileage (" + previousMileage + " km)");
            }

            vehicle.setCurrentMileage(reportedMileage);
            vehicleRepository.save(vehicle);

            mileageLogRepository.save(MileageLog.builder()
                    .vehicle(vehicle)
                    .submittedBy(caller)
                    .reportedMileage(reportedMileage)
                    .build());

            vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                    .eventType("MILEAGE_SUBMITTED")
                    .vehicleId(vehicle.getId())
                    .plateNumber(vehicle.getPlateNumber())
                    .description(String.format("%s (%s) reported odometer reading of %.0f km on vehicle %s at trip completion",
                            caller.getName(), caller.getRole().name(), reportedMileage, vehicle.getPlateNumber()))
                    .actorName(caller.getName())
                    .actorRole(caller.getRole().name())
                    .occurredAt(LocalDateTime.now())
                    .build());

            if (vehicle.isMilestoneReached(previousMileage)) {
                publishMaintenanceEvent(vehicle, reportedMileage);
            }
        } else {
            vehicleRepository.save(vehicle);
        }

        return TripRequestResponse.from(request);
    }

    public List<TripRequestResponse> getPendingRequests() {
        return tripRequestRepository.findByStatus(TripRequestStatus.PENDING)
                .stream().map(TripRequestResponse::from).toList();
    }

    public List<TripRequestResponse> getAllRequests() {
        return tripRequestRepository.findAll()
                .stream().map(TripRequestResponse::from).toList();
    }

    public List<TripRequestResponse> getMyRequests() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return tripRequestRepository.findByFieldStaffId(user.getId())
                .stream().map(TripRequestResponse::from).toList();
    }

    public List<TripRequestResponse> getMyApprovedRequests() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return tripRequestRepository.findByFieldStaffIdAndStatus(user.getId(), TripRequestStatus.APPROVED)
                .stream().map(TripRequestResponse::from).toList();
    }

    private TripRequest getRequestOrThrow(Long id) {
        return tripRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip request not found: " + id));
    }

    private void publishMaintenanceEvent(Vehicle vehicle, Double mileageAtTrigger) {
        List<User> managers = userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER);
        if (managers.isEmpty()) return;
        User manager = managers.get(0);
        maintenanceEventProducer.publish(MaintenanceFlagCreatedEvent.builder()
                .vehicleId(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .mileageAtTrigger(mileageAtTrigger)
                .fleetManagerId(manager.getId())
                .fleetManagerEmail(manager.getEmail())
                .fleetManagerName(manager.getName())
                .occurredAt(LocalDateTime.now())
                .build());
    }

    private void notifyFleetManagersOfNewRequest(User fieldStaff, Vehicle vehicle, TripRequestCreate dto) {
        List<User> managers = userRepository.findByRoleAndActiveTrue(UserRole.FLEET_MANAGER);
        if (managers.isEmpty()) {
            return;
        }
        managers.forEach(manager -> notificationEventProducer.publish(
                NotificationRequestEvent.builder()
                        .recipientEmail(manager.getEmail())
                        .recipientName(manager.getName())
                        .subject("New Trip Request: Vehicle " + vehicle.getPlateNumber())
                        .message(String.format(
                                "Hi %s,\n\n%s has submitted a trip request.\n\n" +
                                "Vehicle: %s\nDestination: %s\nDates: %s – %s\n\n" +
                                "Please review and approve or reject the request.\n\nFleetOps System",
                                manager.getName(),
                                fieldStaff.getName(),
                                vehicle.getPlateNumber(),
                                dto.getDestination(),
                                dto.getStartDate(),
                                dto.getEndDate()))
                        .type("TRIP_REQUESTED")
                        .occurredAt(LocalDateTime.now())
                        .build()));
    }
}
