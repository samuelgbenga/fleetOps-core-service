package com.fleetops.core.kafka.producer;

import com.fleetops.core.kafka.event.VehicleActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleActivityProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.vehicle-activity}")
    private String topic;

    public void publish(VehicleActivityEvent event) {
        log.info("Publishing VehicleActivityEvent type={} vehicle={}", event.getEventType(), event.getPlateNumber());
        kafkaTemplate.send(topic, event.getPlateNumber(), event);
    }
}
