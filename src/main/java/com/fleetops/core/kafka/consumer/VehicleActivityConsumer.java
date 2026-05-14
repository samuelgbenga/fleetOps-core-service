package com.fleetops.core.kafka.consumer;

import com.fleetops.core.activity.entity.VehicleActivityLog;
import com.fleetops.core.activity.repository.VehicleActivityLogRepository;
import com.fleetops.core.kafka.event.VehicleActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
@RequiredArgsConstructor
@Slf4j
public class VehicleActivityConsumer {

    private final VehicleActivityLogRepository activityLogRepository;

    @KafkaListener(
            topics = "${kafka.topics.vehicle-activity}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "vehicleActivityListenerContainerFactory"
    )
    public void onEvent(VehicleActivityEvent event) {
        log.info("Recording activity event type={} vehicle={}", event.getEventType(), event.getPlateNumber());

        VehicleActivityLog log = VehicleActivityLog.builder()
                .vehicleId(event.getVehicleId())
                .plateNumber(event.getPlateNumber())
                .eventType(event.getEventType())
                .description(event.getDescription())
                .actorName(event.getActorName())
                .actorRole(event.getActorRole())
                .occurredAt(event.getOccurredAt())
                .build();

        activityLogRepository.save(log);
    }
}
