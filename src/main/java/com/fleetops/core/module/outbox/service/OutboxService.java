package com.fleetops.core.module.outbox.service;

public interface OutboxService {
    void save(String topic, String eventType, Object payload);
}
