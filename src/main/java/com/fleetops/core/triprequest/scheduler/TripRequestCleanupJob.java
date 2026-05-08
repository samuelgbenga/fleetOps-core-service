package com.fleetops.core.triprequest.scheduler;

import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.triprequest.repository.TripRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripRequestCleanupJob {

    private final TripRequestRepository tripRequestRepository;

    /**
     * Runs daily at midnight. Auto-rejects any PENDING trip request whose start date
     * has already passed — the trip window is gone and the request will never be actionable.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void rejectExpiredPendingRequests() {
        List<TripRequest> expired = tripRequestRepository
                .findByStatusAndStartDateBefore(TripRequestStatus.PENDING, LocalDate.now());

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(req -> req.setStatus(TripRequestStatus.REJECTED));
        tripRequestRepository.saveAll(expired);

        log.info("TripRequestCleanupJob: auto-rejected {} expired pending request(s)", expired.size());
    }
}
