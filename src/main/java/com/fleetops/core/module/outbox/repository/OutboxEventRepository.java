package com.fleetops.core.module.outbox.repository;

import com.fleetops.core.module.outbox.model.OutboxEvent;
import com.fleetops.core.module.outbox.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetries);
}
