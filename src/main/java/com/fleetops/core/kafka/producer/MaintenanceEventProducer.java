package com.fleetops.core.kafka.producer;

import com.fleetops.core.kafka.event.MaintenanceFlagCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MaintenanceEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.maintenance-flag-created}")
    private String topic;

    public void publish(MaintenanceFlagCreatedEvent event) {
        log.info("Publishing MaintenanceFlagCreatedEvent for vehicle: {}", event.getPlateNumber());
        kafkaTemplate.send(topic, String.valueOf(event.getVehicleId()), event);
    }
}
