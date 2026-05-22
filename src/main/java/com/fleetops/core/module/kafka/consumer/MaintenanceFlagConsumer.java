package com.fleetops.core.module.kafka.consumer;

import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.maintenance.model.TriggerType;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MaintenanceFlagConsumer {

    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final VehicleActivityProducer vehicleActivityProducer;

    @KafkaListener(topics = "${kafka.topics.maintenance-events}", groupId = "fleet-core-group")
    @Transactional
    public void consume(MaintenanceFlagCreatedEvent event) {
        try {
            vehicleRepository.findById(event.getVehicleId()).ifPresent(vehicle -> {
                companyRepository.findById(event.getCompanyId()).ifPresent(company -> {
                    vehicle.setStatus(VehicleStatus.MAINTENANCE);
                    vehicleRepository.save(vehicle);

                    MaintenanceFlag flag = MaintenanceFlag.builder()
                            .company(company)
                            .vehicle(vehicle)
                            .triggerType(TriggerType.valueOf(event.getTriggerType()))
                            .status(FlagStatus.OPEN)
                            .description(event.getDescription())
                            .build();
                    maintenanceFlagRepository.save(flag);

                    vehicleActivityProducer.publish(VehicleActivityEvent.builder()
                            .eventType("MAINTENANCE_SCHEDULED")
                            .vehicleId(vehicle.getId())
                            .companyId(company.getId())
                            .plateNumber(vehicle.getPlateNumber())
                            .description("Maintenance flag created for vehicle " + vehicle.getPlateNumber()
                                    + " — trigger: " + event.getTriggerType())
                            .actorName("System")
                            .actorRole("SYSTEM")
                            .occurredAt(LocalDateTime.now())
                            .build());

                    List<com.fleetops.core.module.user.model.User> managers =
                            userRepository.findByRoleAndCompanyId(Role.FLEET_MANAGER, company.getId());

                    for (var manager : managers) {
                        notificationEventProducer.publish(NotificationRequestEvent.builder()
                                .recipientEmail(manager.getEmail())
                                .recipientName(manager.getName())
                                .subject("Maintenance Required: Vehicle " + vehicle.getPlateNumber())
                                .message(String.format(
                                        "Hi %s,\n\nA maintenance flag has been raised for vehicle %s.\nTrigger: %s\n\nFleetOps System",
                                        manager.getName(), vehicle.getPlateNumber(), event.getTriggerType()))
                                .type("MAINTENANCE_FLAG_CREATED")
                                .occurredAt(LocalDateTime.now())
                                .build());
                    }
                });
            });
        } catch (Exception ex) {
            log.error("MaintenanceFlagConsumer failed for flagId {}: {}",
                    event.getFlagId(), ex.getMessage(), ex);
        }
    }
}
