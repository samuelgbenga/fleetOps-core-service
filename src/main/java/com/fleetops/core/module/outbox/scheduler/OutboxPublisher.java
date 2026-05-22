package com.fleetops.core.module.outbox.scheduler;

import com.fleetops.core.module.outbox.model.OutboxEvent;
import com.fleetops.core.module.outbox.model.OutboxStatus;
import com.fleetops.core.module.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_RETRY = 3;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void publishPendingEvents() {
        long start = System.currentTimeMillis();
        List<OutboxEvent> events = outboxEventRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, MAX_RETRY);

        AtomicInteger published = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPayload()).get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                published.incrementAndGet();
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRY) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event [{}] permanently failed after {} retries",
                            event.getEventType(), MAX_RETRY);
                }
                failed.incrementAndGet();
            }
            outboxEventRepository.save(event);
        }

        long elapsed = System.currentTimeMillis() - start;
        if (!events.isEmpty()) {
            log.info("Outbox publisher: processed={} published={} failed={} duration={}ms",
                    events.size(), published.get(), failed.get(), elapsed);
        }
    }
}
