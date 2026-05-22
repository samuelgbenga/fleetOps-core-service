package com.fleetops.core.module.kafka.producer;

import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VehicleActivityProducer {

    private final OutboxService outboxService;

    @Value("${kafka.topics.vehicle-activity}")
    private String topic;

    public void publish(VehicleActivityEvent event) {
        outboxService.save(topic, "VehicleActivityEvent", event);
    }
}
