package com.fleetops.core.module.maintenance.service.impl;

import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.maintenance.dto.*;
import com.fleetops.core.module.maintenance.model.*;
import com.fleetops.core.module.maintenance.repository.*;
import com.fleetops.core.module.maintenance.service.MaintenanceFlagService;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleService;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.QuotationException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceFlagServiceImpl implements MaintenanceFlagService {

    private final MaintenanceFlagRepository flagRepository;
    private final MaintenanceQuotationRepository quotationRepository;
    private final MaintenanceRatingRepository ratingRepository;
    private final MaintenanceMessageRepository messageRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final VehicleActivityProducer vehicleActivityProducer;
    private final VehicleService vehicleService;

    @Override
    @Transactional
    public MaintenanceFlagResponse createFlag(CreateFlagRequest request) {
        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        var vehicle = vehicleRepository.findByIdAndCompanyId(request.getVehicleId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        User requester = currentUser();

        MaintenanceFlag flag = flagRepository.save(MaintenanceFlag.builder()
                .company(company)
                .vehicle(vehicle)
                .requestedByUser(requester)
                .triggerType(TriggerType.MANUAL)
                .status(FlagStatus.OPEN)
                .description(request.getDescription())
                .build());

        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        vehicleRepository.save(vehicle);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("MAINTENANCE_FLAG_CREATED")
                .vehicleId(vehicle.getId())
                .companyId(companyId)
                .plateNumber(vehicle.getPlateNumber())
                .description("Manual maintenance flag raised by " + requester.getName())
                .actorName(requester.getName())
                .actorRole(requester.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    public List<MaintenanceFlagResponse> getAll() {
        return flagRepository.findByCompanyId(TenantContext.getCompanyId())
                .stream().map(MaintenanceFlagResponse::from).toList();
    }

    @Override
    public MaintenanceFlagResponse getById(Long id) {
        MaintenanceFlag flag = getOrThrow(id);
        var latestQuotation = quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(flag.getId()).orElse(null);
        return MaintenanceFlagResponse.from(flag, latestQuotation);
    }

    @Override
    public List<MaintenanceFlagResponse> getByVehicle(Long vehicleId) {
        return flagRepository.findByVehicleIdAndCompanyId(vehicleId, TenantContext.getCompanyId())
                .stream().map(MaintenanceFlagResponse::from).toList();
    }

    @Override
    public List<MaintenanceFlagResponse> getMyCrew() {
        User crew = currentUser();
        return flagRepository.findByAssignedCrewId(crew.getId())
                .stream().map(MaintenanceFlagResponse::from).toList();
    }

    @Override
    @Transactional
    public MaintenanceFlagResponse assignCrew(Long flagId, AssignCrewRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.OPEN) {
            throw new ConflictException("Flag must be OPEN to assign crew");
        }
        User crew = userRepository.findById(request.getCrewId())
                .filter(u -> u.getRole() == Role.MAINTENANCE_CREW)
                .orElseThrow(() -> new ResourceNotFoundException("Crew member not found"));

        crew.setAvailable(false);
        userRepository.save(crew);

        flag.setAssignedCrew(crew);
        flag.setStatus(FlagStatus.CREW_ASSIGNED);
        flag.setAssignedAt(LocalDateTime.now());
        flagRepository.save(flag);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(crew.getEmail())
                .recipientName(crew.getName())
                .subject("Maintenance Task Assigned: " + flag.getVehicle().getPlateNumber())
                .message(String.format("Hi %s,\n\nYou have been assigned to vehicle %s.\nPlease submit a quotation.\n\nFleetOps",
                        crew.getName(), flag.getVehicle().getPlateNumber()))
                .type("CREW_ASSIGNED")
                .occurredAt(LocalDateTime.now())
                .build());

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    @Transactional
    public QuotationResponse submitQuotation(Long flagId, QuotationRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.CREW_ASSIGNED) {
            throw new QuotationException("Flag must be in CREW_ASSIGNED status to submit quotation");
        }
        User crew = currentUser();
        var company = flag.getCompany();

        MaintenanceQuotation quotation = quotationRepository.save(MaintenanceQuotation.builder()
                .flag(flag)
                .crew(crew)
                .company(company)
                .estimatedCost(request.getEstimatedCost())
                .description(request.getDescription())
                .partsNeeded(request.getPartsNeeded())
                .revisionNumber(1)
                .build());

        flag.setStatus(FlagStatus.QUOTE_SUBMITTED);
        flagRepository.save(flag);

        notifyFleetManagers(company.getId(), flag, "Quotation Submitted",
                String.format("A quotation of ₦%s has been submitted for vehicle %s.",
                        request.getEstimatedCost(), flag.getVehicle().getPlateNumber()),
                "QUOTATION_SUBMITTED");

        return QuotationResponse.from(quotation);
    }

    @Override
    @Transactional
    public QuotationResponse reviseQuotation(Long flagId, QuotationRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.QUOTE_REJECTED) {
            throw new QuotationException("Flag must be in QUOTE_REJECTED status to revise quotation");
        }

        MaintenanceQuotation last = quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(flagId)
                .orElseThrow(() -> new QuotationException("No existing quotation found"));

        MaintenanceQuotation revised = quotationRepository.save(MaintenanceQuotation.builder()
                .flag(flag)
                .crew(last.getCrew())
                .company(flag.getCompany())
                .estimatedCost(request.getEstimatedCost())
                .description(request.getDescription())
                .partsNeeded(request.getPartsNeeded())
                .revisionNumber(last.getRevisionNumber() + 1)
                .build());

        flag.setStatus(FlagStatus.QUOTE_SUBMITTED);
        flagRepository.save(flag);

        return QuotationResponse.from(revised);
    }

    @Override
    @Transactional
    public MaintenanceFlagResponse approveQuotation(Long flagId) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.QUOTE_SUBMITTED) {
            throw new ConflictException("No pending quotation to approve");
        }
        var quotation = quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(flagId)
                .orElseThrow(() -> new QuotationException("No quotation found"));

        quotation.setStatus(QuotationStatus.APPROVED);
        quotation.setReviewedAt(LocalDateTime.now());
        quotationRepository.save(quotation);

        flag.setStatus(FlagStatus.QUOTE_APPROVED);
        flagRepository.save(flag);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(quotation.getCrew().getEmail())
                .recipientName(quotation.getCrew().getName())
                .subject("Quotation Approved — " + flag.getVehicle().getPlateNumber())
                .message("Your quotation has been approved. You may now begin work.")
                .type("QUOTATION_APPROVED")
                .occurredAt(LocalDateTime.now())
                .build());

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    @Transactional
    public MaintenanceFlagResponse rejectQuotation(Long flagId, RejectQuotationRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.QUOTE_SUBMITTED) {
            throw new ConflictException("No pending quotation to reject");
        }
        var quotation = quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(flagId)
                .orElseThrow(() -> new QuotationException("No quotation found"));

        quotation.setStatus(QuotationStatus.REJECTED);
        quotation.setRejectionReason(request.getReason());
        quotation.setReviewedAt(LocalDateTime.now());
        quotationRepository.save(quotation);

        flag.setStatus(FlagStatus.QUOTE_REJECTED);
        flagRepository.save(flag);

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(quotation.getCrew().getEmail())
                .recipientName(quotation.getCrew().getName())
                .subject("Quotation Rejected — " + flag.getVehicle().getPlateNumber())
                .message("Reason: " + request.getReason() + "\n\nPlease revise and resubmit.")
                .type("QUOTATION_REJECTED")
                .occurredAt(LocalDateTime.now())
                .build());

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    @Transactional
    public MaintenanceFlagResponse updateProgress(Long flagId, ProgressUpdateRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.QUOTE_APPROVED && flag.getStatus() != FlagStatus.IN_PROGRESS) {
            throw new ConflictException("Flag must be QUOTE_APPROVED or IN_PROGRESS");
        }
        flag.setProgressNotes(request.getProgressNotes());
        flag.setStatus(FlagStatus.IN_PROGRESS);
        flagRepository.save(flag);

        notifyFleetManagers(flag.getCompany().getId(), flag, "Maintenance Progress Update",
                "Progress update on vehicle " + flag.getVehicle().getPlateNumber() + ": " + request.getProgressNotes(),
                "MAINTENANCE_PROGRESS");

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    @Transactional
    public MaintenanceFlagResponse markDone(Long flagId) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.QUOTE_APPROVED && flag.getStatus() != FlagStatus.IN_PROGRESS) {
            throw new ConflictException("Flag must be QUOTE_APPROVED or IN_PROGRESS to mark done");
        }
        flag.setStatus(FlagStatus.PENDING_APPROVAL);
        flagRepository.save(flag);

        notifyFleetManagers(flag.getCompany().getId(), flag, "Maintenance Done — Approval Required",
                "Work on vehicle " + flag.getVehicle().getPlateNumber() + " is done. Please review and resolve.",
                "MAINTENANCE_DONE");

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    @Transactional
    public MaintenanceFlagResponse resolve(Long flagId, RatingRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        if (flag.getStatus() != FlagStatus.PENDING_APPROVAL) {
            throw new ConflictException("Flag must be PENDING_APPROVAL to resolve");
        }
        User rater = currentUser();
        User crew = flag.getAssignedCrew();

        if (ratingRepository.existsByFlagIdAndRatedByUserId(flagId, rater.getId())) {
            throw new ConflictException("Already rated this maintenance job");
        }

        ratingRepository.save(MaintenanceRating.builder()
                .flag(flag)
                .crew(crew)
                .company(flag.getCompany())
                .ratedByUser(rater)
                .stars(request.getStars())
                .comment(request.getComment())
                .build());

        Double avgRating = ratingRepository.computeAverageRatingForCrew(crew.getId());
        long jobCount = ratingRepository.countByCrewId(crew.getId());
        crew.setAverageRating(avgRating);
        crew.setTotalJobsCompleted((int) jobCount);
        crew.setAvailable(true);
        userRepository.save(crew);

        if (request.getActualCost() != null) {
            var vehicle = flag.getVehicle();
            vehicle.setTotalMaintenanceSpend(
                    vehicle.getTotalMaintenanceSpend().add(request.getActualCost()));
            vehicleRepository.save(vehicle);

            quotationRepository.findTopByFlagIdOrderByRevisionNumberDesc(flagId).ifPresent(q -> {
                q.setActualCost(request.getActualCost());
                quotationRepository.save(q);
            });
        }

        var vehicle = flag.getVehicle();
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(vehicle);
        vehicleService.recomputeHealthForVehicle(vehicle);
        vehicleRepository.save(vehicle);

        flag.setStatus(FlagStatus.RESOLVED);
        flag.setResolvedAt(LocalDateTime.now());
        flagRepository.save(flag);

        vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                .eventType("MAINTENANCE_RESOLVED")
                .vehicleId(vehicle.getId())
                .companyId(flag.getCompany().getId())
                .plateNumber(vehicle.getPlateNumber())
                .description("Maintenance resolved. Crew: " + crew.getName()
                        + ". Rating: " + request.getStars() + "/5")
                .actorName(rater.getName())
                .actorRole(rater.getRole().name())
                .occurredAt(LocalDateTime.now())
                .build());

        notificationEventProducer.publish(NotificationRequestEvent.builder()
                .recipientEmail(crew.getEmail())
                .recipientName(crew.getName())
                .subject("Maintenance Resolved — " + vehicle.getPlateNumber())
                .message("Job resolved. Rating: " + request.getStars() + "/5. Thank you!")
                .type("MAINTENANCE_RESOLVED")
                .occurredAt(LocalDateTime.now())
                .build());

        return MaintenanceFlagResponse.from(flag);
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long flagId, MessageRequest request) {
        MaintenanceFlag flag = getOrThrow(flagId);
        User sender = currentUser();
        MaintenanceMessage msg = messageRepository.save(MaintenanceMessage.builder()
                .flag(flag)
                .sender(sender)
                .senderName(sender.getName())
                .senderRole(sender.getRole().name())
                .message(request.getMessage())
                .build());
        return MessageResponse.from(msg);
    }

    @Override
    public List<MessageResponse> getMessages(Long flagId) {
        getOrThrow(flagId);
        return messageRepository.findByFlagIdOrderBySentAtAsc(flagId)
                .stream().map(MessageResponse::from).toList();
    }

    @Override
    public List<MaintenanceFlagResponse> getAllPlatform() {
        return flagRepository.findAll().stream().map(MaintenanceFlagResponse::from).toList();
    }

    private void notifyFleetManagers(Long companyId, MaintenanceFlag flag,
                                      String subject, String message, String type) {
        userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, companyId)
                .forEach(manager -> notificationEventProducer.publish(NotificationRequestEvent.builder()
                        .recipientEmail(manager.getEmail())
                        .recipientName(manager.getName())
                        .subject(subject)
                        .message(message)
                        .type(type)
                        .occurredAt(LocalDateTime.now())
                        .build()));
    }

    private MaintenanceFlag getOrThrow(Long id) {
        if (currentUser().getRole() == Role.MAINTENANCE_CREW) {
            return flagRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Flag not found: " + id));
        }
        return flagRepository.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Flag not found: " + id));
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
