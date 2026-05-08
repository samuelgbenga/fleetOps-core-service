package com.fleetops.core.triprequest.service;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.exception.VehicleNotAvailableException;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.triprequest.dto.TripRequestCreate;
import com.fleetops.core.triprequest.dto.TripRequestResponse;
import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
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

        return TripRequestResponse.from(tripRequestRepository.save(tripRequest));
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

        return TripRequestResponse.from(request);
    }

    @Transactional
    public TripRequestResponse completeTrip(Long id) {
        TripRequest request = getRequestOrThrow(id);

        if (request.getStatus() != TripRequestStatus.APPROVED) {
            throw new ConflictException("Only APPROVED trips can be marked as completed");
        }

        request.setStatus(TripRequestStatus.COMPLETED);
        tripRequestRepository.save(request);

        request.getVehicle().setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(request.getVehicle());

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

    private TripRequest getRequestOrThrow(Long id) {
        return tripRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip request not found: " + id));
    }
}
