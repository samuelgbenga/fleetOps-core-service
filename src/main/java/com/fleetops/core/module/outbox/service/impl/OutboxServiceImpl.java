package com.fleetops.core.module.outbox.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fleetops.core.module.outbox.model.OutboxEvent;
import com.fleetops.core.module.outbox.repository.OutboxEventRepository;
import com.fleetops.core.module.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.builder()
                    .topic(topic)
                    .eventType(eventType)
                    .payload(json)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event [{}]: {}", eventType, e.getMessage());
            throw new RuntimeException("Outbox serialization failed", e);
        }
    }
}
