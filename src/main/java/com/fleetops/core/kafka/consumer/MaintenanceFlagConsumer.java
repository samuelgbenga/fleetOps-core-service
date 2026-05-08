package com.fleetops.core.kafka.consumer;

import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.kafka.event.NotificationRequestEvent;
import com.fleetops.core.kafka.producer.NotificationEventProducer;
import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import com.fleetops.core.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
//@Profile("!dev")
@RequiredArgsConstructor
@Slf4j
public class MaintenanceFlagConsumer {

    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationEventProducer notificationEventProducer;

    @KafkaListener(
            topics = "${kafka.topics.maintenance-flag-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onEvent(MaintenanceFlagCreatedEvent event) {
        log.info("Received MaintenanceFlagCreatedEvent for vehicleId={}", event.getVehicleId());

        // 1. Set vehicle status to MAINTENANCE
        vehicleRepository.findById(event.getVehicleId()).ifPresent(vehicle -> {
            vehicle.setStatus(VehicleStatus.MAINTENANCE);
            vehicleRepository.save(vehicle);
            log.info("Vehicle {} status set to MAINTENANCE", vehicle.getPlateNumber());

            // 2. Create the maintenance flag record
            MaintenanceFlag flag = MaintenanceFlag.builder()
                    .vehicle(vehicle)
                    .mileageAtTrigger(event.getMileageAtTrigger())
                    .build();
            maintenanceFlagRepository.save(flag);
            log.info("MaintenanceFlag created for vehicle {}", vehicle.getPlateNumber());
        });

        // 3. Notify the fleet manager via notification-service
        NotificationRequestEvent notification = NotificationRequestEvent.builder()
                .recipientEmail(event.getFleetManagerEmail())
                .recipientName(event.getFleetManagerName())
                .subject("Maintenance Required: Vehicle " + event.getPlateNumber())
                .message(String.format(
                        "Hi %s,\n\nVehicle %s has reached the %.0f km mileage milestone " +
                        "and has been flagged for maintenance.\n\n" +
                        "Please assign a maintenance team member to this vehicle.\n\n" +
                        "FleetOps System",
                        event.getFleetManagerName(),
                        event.getPlateNumber(),
                        event.getMileageAtTrigger()
                ))
                .type("MAINTENANCE_FLAG_RAISED")
                .occurredAt(LocalDateTime.now())
                .build();

        notificationEventProducer.publish(notification);
    }
}
