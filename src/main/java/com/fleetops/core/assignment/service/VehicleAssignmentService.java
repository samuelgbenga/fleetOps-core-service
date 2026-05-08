package com.fleetops.core.assignment.service;

import com.fleetops.core.assignment.dto.AssignmentResponse;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleAssignmentService {

    private final VehicleAssignmentRepository vehicleAssignmentRepository;

    public List<AssignmentResponse> getAssignmentsByVehicle(Long vehicleId) {
        return vehicleAssignmentRepository.findByVehicleId(vehicleId)
                .stream().map(AssignmentResponse::from).toList();
    }
}
