package com.fleetops.core.kafka.producer;

import com.fleetops.core.kafka.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.notification-request}")
    private String topic;

    public void publish(NotificationRequestEvent event) {
        log.info("Publishing NotificationRequestEvent type={} to={}", event.getType(), event.getRecipientEmail());
        kafkaTemplate.send(topic, event.getRecipientEmail(), event);
    }
}
