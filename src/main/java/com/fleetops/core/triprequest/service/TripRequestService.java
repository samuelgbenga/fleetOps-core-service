package com.fleetops.core.triprequest.service;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.exception.VehicleNotAvailableException;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class TripRequestService {

    private final TripRequestRepository tripRequestRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public TripRequestResponse createRequest(TripRequestCreate dto) {
        // Get currently authenticated field staff
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User fieldStaff = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Step 1: Check vehicle exists
        Vehicle vehicle = vehicleRepository.findById(dto.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + dto.getVehicleId()));

        // Step 2: Check vehicle is AVAILABLE
        if (!vehicle.isAvailable()) {
            throw new VehicleNotAvailableException(
                    "Vehicle " + vehicle.getPlateNumber() + " is currently " + vehicle.getStatus()
            );
        }

        // Step 3: Check no date overlap
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

        // Create assignment and block vehicle
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

        return TripRequestResponse.from(request);
    }

    @Transactional
    public TripRequestResponse rejectRequest(Long id) {
        TripRequest request = getRequestOrThrow(id);

        if (request.getStatus() != TripRequestStatus.PENDING) {
            throw new ConflictException("Trip request is not in PENDING status");
        }

        request.setStatus(TripRequestStatus.REJECTED);
        return TripRequestResponse.from(tripRequestRepository.save(request));
    }

    public List<TripRequestResponse> getPendingRequests() {
        return tripRequestRepository.findByStatus(TripRequestStatus.PENDING)
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
