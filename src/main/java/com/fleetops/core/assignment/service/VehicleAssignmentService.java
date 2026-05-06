package com.fleetops.core.assignment.service;

import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleAssignmentService {

    private final VehicleAssignmentRepository vehicleAssignmentRepository;

    public List<VehicleAssignment> getAssignmentsByVehicle(Long vehicleId) {
        return vehicleAssignmentRepository.findByVehicleId(vehicleId);
    }
}
