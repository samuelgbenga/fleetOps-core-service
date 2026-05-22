package com.fleetops.core.module.mileage.service.impl;

import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.MaintenanceEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.mileage.dto.MileageLogRequest;
import com.fleetops.core.module.mileage.dto.MileageLogResponse;
import com.fleetops.core.module.mileage.model.MileageLog;
import com.fleetops.core.module.mileage.repository.MileageLogRepository;
import com.fleetops.core.module.mileage.service.MileageLogService;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleService;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MileageLogServiceImpl implements MileageLogService {

    private final MileageLogRepository mileageLogRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final TripRequestRepository tripRequestRepository;
    private final MaintenanceEventProducer maintenanceEventProducer;
    private final VehicleActivityProducer vehicleActivityProducer;
    private final VehicleService vehicleService;

    @Override
    @Transactional
    public MileageLogResponse submit(MileageLogRequest request) {
        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(request.getVehicleId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        if (request.getReportedMileage() <= vehicle.getCurrentMileage()) {
            throw new ConflictException("Reported mileage must be greater than current mileage ("
                    + vehicle.getCurrentMileage() + " km)");
        }

        User submitter = userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var tripRequest = request.getTripRequestId() != null
                ? tripRequestRepository.findById(request.getTripRequestId()).orElse(null)
                : null;

        double oldMileage = vehicle.getCurrentMileage();
        vehicle.setCurrentMileage(request.getReportedMileage());
        vehicleRepository.save(vehicle);

        MileageLog log = mileageLogRepository.save(MileageLog.builder()
                .company(company)
                .vehicle(vehicle)
                .submittedBy(submitter)
                .tripRequest(tripRequest)
                .reportedMileage(request.getReportedMileage())
                .build());

        vehicleService.recomputeHealthForVehicle(vehicle);
        vehicleRepository.save(vehicle);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("MILEAGE_SUBMITTED")
                .vehicleId(vehicle.getId())
                .companyId(companyId)
                .plateNumber(vehicle.getPlateNumber())
                .description(String.format("%s submitted mileage: %.0f km → %.0f km",
                        submitter.getName(), oldMileage, request.getReportedMileage()))
                .actorName(submitter.getName())
                .actorRole(submitter.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        if (vehicle.isMilestoneReached(oldMileage)) {
            maintenanceEventProducer.publish(MaintenanceFlagCreatedEvent.builder()
                    .vehicleId(vehicle.getId())
                    .companyId(companyId)
                    .plateNumber(vehicle.getPlateNumber())
                    .triggerType("MILEAGE")
                    .description("Mileage milestone reached at " + vehicle.getCurrentMileage() + " km")
                    .build());
        }

        return MileageLogResponse.from(log);
    }

    @Override
    public List<MileageLogResponse> getByVehicle(Long vehicleId) {
        return mileageLogRepository.findByVehicleIdAndCompanyId(vehicleId, TenantContext.getCompanyId())
                .stream().map(MileageLogResponse::from).toList();
    }
}
