package com.fleetops.core.module.kafka.producer;

import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final OutboxService outboxService;

    @Value("${kafka.topics.notification-request}")
    private String topic;

    public void publish(NotificationRequestEvent event) {
        outboxService.save(topic, "NotificationRequestEvent", event);
    }
}
