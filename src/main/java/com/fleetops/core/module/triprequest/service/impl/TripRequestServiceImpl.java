package com.fleetops.core.module.triprequest.service.impl;

import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.triprequest.dto.RejectTripRequest;
import com.fleetops.core.module.triprequest.dto.TripRequestCreate;
import com.fleetops.core.module.triprequest.dto.TripRequestResponse;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.model.VehicleAssignment;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.triprequest.repository.VehicleAssignmentRepository;
import com.fleetops.core.module.triprequest.service.TripRequestService;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripRequestServiceImpl implements TripRequestService {

    private final TripRequestRepository tripRequestRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final VehicleActivityProducer vehicleActivityProducer;

    @Override
    @Transactional
    public TripRequestResponse create(TripRequestCreate req) {
        if (!req.getStartDate().isAfter(LocalDate.now())) {
            throw new ConflictException("Trip start date must be at least one day in advance");
        }
        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        var vehicle = vehicleRepository.findByIdAndCompanyId(req.getVehicleId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        if (!vehicle.isAvailable()) {
            throw new ConflictException("Vehicle is not available");
        }
        User requester = userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TripRequest trip = tripRequestRepository.save(TripRequest.builder()
                .company(company)
                .vehicle(vehicle)
                .requestedBy(requester)
                .destination(req.getDestination())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build());

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(requester.getEmail())
                .recipientName(requester.getName())
                .subject("Trip Request Submitted")
                .message(String.format("Hi %s,\n\nYour trip request to %s has been submitted and is awaiting approval.\n\nFleetOps System",
                        requester.getName(), req.getDestination()))
                .type("TRIP_REQUESTED")
                .occurredAt(LocalDateTime.now())
                .build());

        userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, companyId)
                .forEach(manager -> notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(manager.getEmail())
                        .recipientName(manager.getName())
                        .subject("New Trip Request: " + vehicle.getPlateNumber())
                        .message(String.format("Hi %s,\n\n%s has requested a trip to %s using vehicle %s.\n\nFleetOps System",
                                manager.getName(), requester.getName(), req.getDestination(), vehicle.getPlateNumber()))
                        .type("TRIP_REQUESTED_MANAGER")
                        .occurredAt(LocalDateTime.now())
                        .build()));

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("TRIP_REQUESTED")
                .vehicleId(vehicle.getId())
                .companyId(companyId)
                .plateNumber(vehicle.getPlateNumber())
                .description(requester.getName() + " requested a trip to " + req.getDestination())
                .actorName(requester.getName())
                .actorRole(requester.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(trip);
    }

    @Override
    public List<TripRequestResponse> getAll() {
        return tripRequestRepository.findByCompanyId(TenantContext.getCompanyId())
                .stream().map(TripRequestResponse::from).toList();
    }

    @Override
    public List<TripRequestResponse> getMy() {
        return tripRequestRepository.findByRequestedByIdAndCompanyId(
                        TenantContext.getUserId(), TenantContext.getCompanyId())
                .stream().map(TripRequestResponse::from).toList();
    }

    @Override
    public List<TripRequestResponse> getMyApproved() {
        return tripRequestRepository.findByRequestedByIdAndCompanyId(
                        TenantContext.getUserId(), TenantContext.getCompanyId())
                .stream()
                .filter(t -> t.getStatus() == TripRequestStatus.APPROVED)
                .map(TripRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public TripRequestResponse approve(Long id) {
        TripRequest trip = getOrThrow(id);
        if (trip.getStatus() != TripRequestStatus.PENDING) {
            throw new ConflictException("Trip is not pending");
        }
        // Re-fetch the vehicle with a pessimistic write lock so concurrent approval
        // requests are serialised at DB level — only one transaction can hold the
        // lock at a time, preventing double-approval of the same vehicle.
        var vehicle = vehicleRepository.findByIdWithLock(trip.getVehicle().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        if (!vehicle.isAvailable()) {
            throw new ConflictException("Vehicle is no longer available");
        }

        vehicle.setStatus(VehicleStatus.ASSIGNED);
        vehicleRepository.save(vehicle);

        User approver = userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        assignmentRepository.save(VehicleAssignment.builder()
                .company(trip.getCompany())
                .tripRequest(trip)
                .vehicle(vehicle)
                .assignedTo(trip.getRequestedBy())
                .build());

        tripRequestRepository.findByVehicleIdAndStatus(vehicle.getId(), TripRequestStatus.PENDING)
                .forEach(other -> {
                    if (!other.getId().equals(id)) {
                        other.setStatus(TripRequestStatus.REJECTED);
                        other.setRejectionReason("Vehicle was approved for another trip");
                        tripRequestRepository.save(other);
                    }
                });

        trip.setStatus(TripRequestStatus.APPROVED);
        trip.setApprovedAt(LocalDateTime.now());
        tripRequestRepository.save(trip);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(trip.getRequestedBy().getEmail())
                .recipientName(trip.getRequestedBy().getName())
                .subject("Trip Request Approved")
                .message("Your trip to " + trip.getDestination() + " has been approved. Vehicle: "
                        + vehicle.getPlateNumber())
                .type("TRIP_APPROVED")
                .occurredAt(LocalDateTime.now())
                .build());

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("TRIP_APPROVED")
                .vehicleId(vehicle.getId())
                .companyId(trip.getCompany().getId())
                .plateNumber(vehicle.getPlateNumber())
                .description("Trip to " + trip.getDestination() + " approved by " + approver.getName())
                .actorName(approver.getName())
                .actorRole(approver.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(trip);
    }

    @Override
    @Transactional
    public TripRequestResponse reject(Long id, RejectTripRequest req) {
        TripRequest trip = getOrThrow(id);
        if (trip.getStatus() != TripRequestStatus.PENDING) {
            throw new ConflictException("Trip is not pending");
        }
        trip.setStatus(TripRequestStatus.REJECTED);
        trip.setRejectionReason(req.getReason());
        tripRequestRepository.save(trip);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(trip.getRequestedBy().getEmail())
                .recipientName(trip.getRequestedBy().getName())
                .subject("Trip Request Rejected")
                .message("Your trip to " + trip.getDestination() + " was rejected. Reason: " + req.getReason())
                .type("TRIP_REJECTED")
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(trip);
    }

    @Override
    @Transactional
    public TripRequestResponse complete(Long id) {
        TripRequest trip = getOrThrow(id);
        if (trip.getStatus() != TripRequestStatus.APPROVED) {
            throw new ConflictException("Trip is not approved");
        }
        trip.setStatus(TripRequestStatus.PENDING_COMPLETION);
        tripRequestRepository.save(trip);

        Long companyId = trip.getCompany().getId();
        userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, companyId)
                .forEach(manager -> notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(manager.getEmail())
                        .recipientName(manager.getName())
                        .subject("Trip Completion Review Required: " + trip.getVehicle().getPlateNumber())
                        .message(String.format(
                                "Hi %s,\n\n%s has marked their trip to %s as completed. Please review and confirm.\n\nFleetOps System",
                                manager.getName(), trip.getRequestedBy().getName(), trip.getDestination()))
                        .type("TRIP_PENDING_COMPLETION")
                        .occurredAt(LocalDateTime.now())
                        .build()));

        return TripRequestResponse.from(trip);
    }

    @Override
    @Transactional
    public TripRequestResponse confirmCompletion(Long id) {
        TripRequest trip = getOrThrow(id);
        if (trip.getStatus() != TripRequestStatus.PENDING_COMPLETION) {
            throw new ConflictException("Trip is not pending completion");
        }
        var vehicle = trip.getVehicle();
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(vehicle);

        trip.setStatus(TripRequestStatus.COMPLETED);
        trip.setCompletedAt(LocalDateTime.now());
        tripRequestRepository.save(trip);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("TRIP_COMPLETED")
                .vehicleId(vehicle.getId())
                .companyId(trip.getCompany().getId())
                .plateNumber(vehicle.getPlateNumber())
                .description("Trip to " + trip.getDestination() + " completed")
                .actorName(trip.getRequestedBy().getName())
                .actorRole(trip.getRequestedBy().getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(trip);
    }

    @Override
    @Transactional
    public TripRequestResponse cancel(Long id) {
        TripRequest trip = getOrThrow(id);
        if (trip.getStatus() != TripRequestStatus.PENDING && trip.getStatus() != TripRequestStatus.APPROVED) {
            throw new ConflictException("Only pending or approved trips can be cancelled");
        }
        trip.setStatus(TripRequestStatus.PENDING_CANCELLATION);
        tripRequestRepository.save(trip);

        Long companyId = trip.getCompany().getId();
        userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, companyId)
                .forEach(manager -> notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(manager.getEmail())
                        .recipientName(manager.getName())
                        .subject("Cancellation Request: " + trip.getVehicle().getPlateNumber())
                        .message(String.format(
                                "Hi %s,\n\n%s has requested to cancel their trip to %s. Please review and approve the cancellation.\n\nFleetOps System",
                                manager.getName(), trip.getRequestedBy().getName(), trip.getDestination()))
                        .type("TRIP_CANCELLATION_REQUESTED")
                        .occurredAt(LocalDateTime.now())
                        .build()));

        return TripRequestResponse.from(trip);
    }

    @Override
    @Transactional
    public TripRequestResponse approveCancellation(Long id) {
        TripRequest trip = getOrThrow(id);
        if (trip.getStatus() != TripRequestStatus.PENDING_CANCELLATION) {
            throw new ConflictException("Trip is not awaiting cancellation approval");
        }
        var vehicle = trip.getVehicle();
        if (vehicle.getStatus() == VehicleStatus.ASSIGNED) {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicleRepository.save(vehicle);
        }
        trip.setStatus(TripRequestStatus.CANCELLED);
        tripRequestRepository.save(trip);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(trip.getRequestedBy().getEmail())
                .recipientName(trip.getRequestedBy().getName())
                .subject("Trip Cancellation Approved")
                .message(String.format(
                        "Hi %s,\n\nYour cancellation request for the trip to %s has been approved.\n\nFleetOps System",
                        trip.getRequestedBy().getName(), trip.getDestination()))
                .type("TRIP_CANCELLED")
                .occurredAt(LocalDateTime.now())
                .build());

        return TripRequestResponse.from(trip);
    }

    @Override
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void autoRejectStaleRequests() {
        long start = System.currentTimeMillis();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        List<TripRequest> stale = tripRequestRepository.findStalePendingRequests(cutoff);
        stale.forEach(t -> {
            t.setStatus(TripRequestStatus.REJECTED);
            t.setRejectionReason("Auto-rejected: no action taken within 3 days");
        });
        tripRequestRepository.saveAll(stale);
        log.info("Auto-rejected stale trip requests: count={} duration={}ms",
                stale.size(), System.currentTimeMillis() - start);
    }

    private TripRequest getOrThrow(Long id) {
        return tripRequestRepository.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip request not found: " + id));
    }
}
