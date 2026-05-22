package com.fleetops.core.module.breakdown.service.impl;

import com.fleetops.core.module.breakdown.dto.BreakdownReportRequest;
import com.fleetops.core.module.breakdown.dto.BreakdownResponse;
import com.fleetops.core.module.breakdown.dto.DispatchCrewRequest;
import com.fleetops.core.module.breakdown.dto.DispatchReplacementRequest;
import com.fleetops.core.module.breakdown.model.BreakdownReport;
import com.fleetops.core.module.breakdown.model.BreakdownStatus;
import com.fleetops.core.module.breakdown.repository.BreakdownRepository;
import com.fleetops.core.module.breakdown.service.BreakdownService;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.BreakdownReportedEvent;
import com.fleetops.core.module.kafka.event.BreakdownResolvedEvent;
import com.fleetops.core.module.kafka.event.CrewDispatchedEvent;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.ReplacementDispatchedEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.maintenance.model.TriggerType;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleService;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.BreakdownException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreakdownServiceImpl implements BreakdownService {

    private final BreakdownRepository breakdownRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRequestRepository tripRequestRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final VehicleActivityProducer vehicleActivityProducer;
    private final NotificationEventProducer notificationEventProducer;
    private final VehicleService vehicleService;

    @Value("${kafka.topics.breakdown-events}")
    private String breakdownTopic;

    @Override
    @Transactional
    public BreakdownResponse report(BreakdownReportRequest request) {
        Long companyId = TenantContext.getCompanyId();
        Long userId = TenantContext.getUserId();

        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        var vehicle = vehicleRepository.findByIdAndCompanyId(request.getVehicleId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        var fieldStaff = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean hasApprovedTrip = tripRequestRepository
                .findByRequestedByIdAndVehicleIdAndStatus(userId, vehicle.getId(), TripRequestStatus.APPROVED)
                .isPresent();
        if (!hasApprovedTrip) {
            throw new BreakdownException("You do not have an active approved trip for this vehicle");
        }

        vehicle.setStatus(VehicleStatus.BROKEN_DOWN);
        vehicleRepository.save(vehicle);

        var reportBuilder = BreakdownReport.builder()
                .company(company)
                .vehicle(vehicle)
                .fieldStaff(fieldStaff)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .locationDescription(request.getLocationDescription())
                .description(request.getDescription())
                .status(BreakdownStatus.REPORTED);

        if (request.getTripRequestId() != null) {
            tripRequestRepository.findByIdAndCompanyId(request.getTripRequestId(), companyId)
                    .ifPresent(trip -> {
                        trip.setStatus(TripRequestStatus.BROKEN_DOWN);
                        tripRequestRepository.save(trip);
                        reportBuilder.tripRequest(trip);
                    });
        }

        var report = breakdownRepository.save(reportBuilder.build());

        vehicleService.recomputeHealthForVehicle(vehicle);

        List<User> fleetManagers = userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, companyId);

        for (var manager : fleetManagers) {
            notificationEventProducer.publish(NotificationRequestEvent.builder()
                    .recipientEmail(manager.getEmail())
                    .recipientName(manager.getName())
                    .subject("Breakdown Reported: Vehicle " + vehicle.getPlateNumber())
                    .message(String.format(
                            "Hi %s,\n\nA breakdown has been reported for vehicle %s.\nLocation: %.4f, %.4f\nDescription: %s\n\nFleetOps System",
                            manager.getName(), vehicle.getPlateNumber(),
                            request.getLatitude(), request.getLongitude(), request.getDescription()))
                    .type("BREAKDOWN_REPORTED")
                    .occurredAt(LocalDateTime.now())
                    .build());
        }

        List<User> platformAdmins = userRepository.findByRole(Role.PLATFORM_ADMIN);
        for (var admin : platformAdmins) {
            notificationEventProducer.publish(NotificationRequestEvent.builder()
                    .recipientEmail(admin.getEmail())
                    .recipientName(admin.getName())
                    .subject("Breakdown Alert: Vehicle " + vehicle.getPlateNumber())
                    .message(String.format(
                            "Hi %s,\n\nBreakdown reported for vehicle %s (Company ID: %d).\nLocation: %.4f, %.4f\n\nFleetOps System",
                            admin.getName(), vehicle.getPlateNumber(), companyId,
                            request.getLatitude(), request.getLongitude()))
                    .type("BREAKDOWN_REPORTED")
                    .occurredAt(LocalDateTime.now())
                    .build());
        }

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("BREAKDOWN_REPORTED")
                .vehicleId(vehicle.getId())
                .companyId(companyId)
                .plateNumber(vehicle.getPlateNumber())
                .description("Breakdown reported for vehicle " + vehicle.getPlateNumber()
                        + " — " + request.getDescription())
                .actorName(fieldStaff.getName())
                .actorRole(fieldStaff.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        return BreakdownResponse.from(report);
    }

    @Override
    @Transactional
    public BreakdownResponse dispatchReplacement(Long id, DispatchReplacementRequest request) {
        Long companyId = TenantContext.getCompanyId();

        var report = breakdownRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Breakdown report not found"));

        if (report.getStatus() != BreakdownStatus.REPORTED) {
            throw new BreakdownException("Replacement can only be dispatched when breakdown is in REPORTED status");
        }

        var replacement = vehicleRepository.findByIdAndCompanyId(request.getReplacementVehicleId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Replacement vehicle not found"));

        if (replacement.getStatus() != VehicleStatus.AVAILABLE) {
            throw new BreakdownException("Replacement vehicle is not available");
        }

        replacement.setStatus(VehicleStatus.ASSIGNED);
        vehicleRepository.save(replacement);

        report.setReplacementVehicle(replacement);
        report.setStatus(BreakdownStatus.REPLACEMENT_DISPATCHED);
        breakdownRepository.save(report);

        var fieldStaff = report.getFieldStaff();
        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(fieldStaff.getEmail())
                .recipientName(fieldStaff.getName())
                .subject("Replacement Vehicle Dispatched")
                .message(String.format(
                        "Hi %s,\n\nA replacement vehicle (%s) has been dispatched to your location.\n\nFleetOps System",
                        fieldStaff.getName(), replacement.getPlateNumber()))
                .type("REPLACEMENT_DISPATCHED")
                .occurredAt(LocalDateTime.now())
                .build());

        return BreakdownResponse.from(report);
    }

    @Override
    @Transactional
    public BreakdownResponse dispatchCrew(Long id, DispatchCrewRequest request) {
        var report = breakdownRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Breakdown report not found"));

        if (report.getStatus() != BreakdownStatus.REPORTED
                && report.getStatus() != BreakdownStatus.REPLACEMENT_DISPATCHED) {
            throw new BreakdownException("Crew can only be dispatched for REPORTED or REPLACEMENT_DISPATCHED breakdowns");
        }

        var crew = userRepository.findById(request.getCrewId())
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found"));

        if (crew.getRole() != Role.MAINTENANCE_CREW) {
            throw new BreakdownException("Assigned user is not a maintenance crew member");
        }

        if (Boolean.FALSE.equals(crew.getAvailable())) {
            throw new BreakdownException("Crew member is not available");
        }

        var flag = MaintenanceFlag.builder()
                .company(report.getCompany())
                .vehicle(report.getVehicle())
                .assignedCrew(crew)
                .triggerType(TriggerType.BREAKDOWN)
                .status(FlagStatus.CREW_ASSIGNED)
                .description("Breakdown dispatch: " + report.getDescription())
                .build();
        maintenanceFlagRepository.save(flag);

        crew.setAvailable(false);
        userRepository.save(crew);

        report.setAssignedCrew(crew);
        report.setMaintenanceFlag(flag);
        report.setStatus(BreakdownStatus.CREW_DISPATCHED);
        breakdownRepository.save(report);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(crew.getEmail())
                .recipientName(crew.getName())
                .subject("Breakdown Assignment: Vehicle " + report.getVehicle().getPlateNumber())
                .message(String.format(
                        "Hi %s,\n\nYou have been dispatched to a breakdown.\nVehicle: %s\nGPS: %.4f, %.4f\nDescription: %s\n\nFleetOps System",
                        crew.getName(), report.getVehicle().getPlateNumber(),
                        report.getLatitude(), report.getLongitude(), report.getDescription()))
                .type("CREW_DISPATCHED")
                .occurredAt(LocalDateTime.now())
                .build());

        return BreakdownResponse.from(report);
    }

    @Override
    @Transactional
    public BreakdownResponse resolve(Long id) {
        Long companyId = TenantContext.getCompanyId();

        var report = breakdownRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Breakdown report not found"));

        if (report.getStatus() == BreakdownStatus.RESOLVED) {
            throw new BreakdownException("Breakdown is already resolved");
        }

        report.setStatus(BreakdownStatus.RESOLVED);
        report.setResolvedAt(LocalDateTime.now());
        breakdownRepository.save(report);

        var vehicle = report.getVehicle();
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(vehicle);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("BREAKDOWN_RESOLVED")
                .vehicleId(vehicle.getId())
                .companyId(companyId)
                .plateNumber(vehicle.getPlateNumber())
                .description("Breakdown resolved for vehicle " + vehicle.getPlateNumber())
                .actorName("Fleet Manager")
                .actorRole(Role.FLEET_MANAGER.name())
                .occurredAt(LocalDateTime.now())
                .build());

        return BreakdownResponse.from(report);
    }

    @Override
    public BreakdownResponse getById(Long id) {
        Long companyId = TenantContext.getCompanyId();
        if (TenantContext.isPlatformLevel()) {
            var report = breakdownRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Breakdown report not found"));
            return BreakdownResponse.from(report);
        }
        var report = breakdownRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Breakdown report not found"));
        return BreakdownResponse.from(report);
    }

    @Override
    public List<BreakdownResponse> getAll() {
        Long companyId = TenantContext.getCompanyId();
        return breakdownRepository.findByCompanyId(companyId)
                .stream()
                .map(BreakdownResponse::from)
                .toList();
    }

    @Override
    public List<BreakdownResponse> getAllPlatform() {
        return breakdownRepository.findAll()
                .stream()
                .map(BreakdownResponse::from)
                .toList();
    }
}
