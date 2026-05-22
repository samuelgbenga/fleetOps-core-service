package com.fleetops.core.module.kafka.consumer;

import com.fleetops.core.module.activity.model.VehicleActivityLog;
import com.fleetops.core.module.activity.repository.VehicleActivityLogRepository;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleActivityConsumer {

    private final VehicleActivityLogRepository activityLogRepository;
    private final CompanyRepository companyRepository;

    @KafkaListener(topics = "${kafka.topics.vehicle-activity}", groupId = "fleet-core-group")
    public void consume(VehicleActivityEvent event) {
        try {
            companyRepository.findById(event.getCompanyId()).ifPresent(company -> {
                VehicleActivityLog activityLog = VehicleActivityLog.builder()
                        .company(company)
                        .vehicleId(event.getVehicleId())
                        .plateNumber(event.getPlateNumber())
                        .eventType(event.getEventType())
                        .description(event.getDescription())
                        .actorName(event.getActorName())
                        .actorRole(event.getActorRole())
                        .occurredAt(event.getOccurredAt())
                        .build();
                activityLogRepository.save(activityLog);
            });
        } catch (Exception ex) {
            log.error("VehicleActivityConsumer failed for event type {}: {}",
                    event.getEventType(), ex.getMessage(), ex);
        }
    }
}
