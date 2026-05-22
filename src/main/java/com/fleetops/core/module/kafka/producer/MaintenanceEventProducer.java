package com.fleetops.core.module.kafka.producer;

import com.fleetops.core.module.kafka.event.MaintenanceFlagCreatedEvent;
import com.fleetops.core.module.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaintenanceEventProducer {

    private final OutboxService outboxService;

    @Value("${kafka.topics.maintenance-events}")
    private String topic;

    public void publish(MaintenanceFlagCreatedEvent event) {
        outboxService.save(topic, "MaintenanceFlagCreatedEvent", event);
    }
}
