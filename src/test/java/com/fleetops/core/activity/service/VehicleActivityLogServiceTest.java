package com.fleetops.core.activity.service;

import com.fleetops.core.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.activity.entity.VehicleActivityLog;
import com.fleetops.core.activity.repository.VehicleActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleActivityLogServiceTest {

    @Mock private VehicleActivityLogRepository repository;
    @InjectMocks private VehicleActivityLogService service;

    @Test
    void search_noFilters_returnsAllOrderedDesc() {
        when(repository.findAllByOrderByOccurredAtDesc()).thenReturn(List.of(
                log(1L, "KJA-001AB", "TRIP_REQUESTED", LocalDateTime.now()),
                log(2L, "ABJ-002CD", "TRIP_APPROVED", LocalDateTime.now().minusHours(1))
        ));

        List<VehicleActivityLogResponse> result = service.search(null, null);

        assertThat(result).hasSize(2);
        verify(repository).findAllByOrderByOccurredAtDesc();
    }

    @Test
    void search_byPlateNumber_delegatesToPlateQuery() {
        when(repository.findByPlateNumberOrderByOccurredAtDesc("KJA-001AB"))
                .thenReturn(List.of(log(1L, "KJA-001AB", "MILEAGE_SUBMITTED", LocalDateTime.now())));

        List<VehicleActivityLogResponse> result = service.search("KJA-001AB", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlateNumber()).isEqualTo("KJA-001AB");
        verify(repository).findByPlateNumberOrderByOccurredAtDesc("KJA-001AB");
    }

    @Test
    void search_byDate_delegatesToDateRangeQuery() {
        LocalDate date = LocalDate.of(2026, 5, 10);
        when(repository.findByOccurredAtBetweenOrderByOccurredAtDesc(any(), any()))
                .thenReturn(List.of(log(1L, "KJA-001AB", "MAINTENANCE_SCHEDULED", LocalDateTime.now())));

        List<VehicleActivityLogResponse> result = service.search(null, date);

        assertThat(result).hasSize(1);
        verify(repository).findByOccurredAtBetweenOrderByOccurredAtDesc(
                eq(date.atStartOfDay()), any());
    }

    @Test
    void search_byPlateAndDate_delegatesToCombinedQuery() {
        LocalDate date = LocalDate.of(2026, 5, 10);
        when(repository.findByPlateNumberAndOccurredAtBetweenOrderByOccurredAtDesc(eq("KJA-001AB"), any(), any()))
                .thenReturn(List.of(log(1L, "KJA-001AB", "TRIP_APPROVED", LocalDateTime.now())));

        List<VehicleActivityLogResponse> result = service.search("KJA-001AB", date);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("TRIP_APPROVED");
        verify(repository).findByPlateNumberAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq("KJA-001AB"), eq(date.atStartOfDay()), any());
    }

    @Test
    void search_noMatchingLogs_returnsEmptyList() {
        when(repository.findByPlateNumberOrderByOccurredAtDesc("ZZZ-999ZZ")).thenReturn(List.of());

        assertThat(service.search("ZZZ-999ZZ", null)).isEmpty();
    }

    @Test
    void search_responseFieldsMappedCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        when(repository.findAllByOrderByOccurredAtDesc()).thenReturn(List.of(
                log(5L, "KJA-001AB", "MILEAGE_SUBMITTED", now)
        ));

        VehicleActivityLogResponse r = service.search(null, null).get(0);

        assertThat(r.getId()).isEqualTo(5L);
        assertThat(r.getPlateNumber()).isEqualTo("KJA-001AB");
        assertThat(r.getEventType()).isEqualTo("MILEAGE_SUBMITTED");
        assertThat(r.getActorName()).isEqualTo("Test Actor");
        assertThat(r.getOccurredAt()).isEqualTo(now);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private VehicleActivityLog log(Long id, String plate, String eventType, LocalDateTime occurredAt) {
        return VehicleActivityLog.builder()
                .id(id)
                .vehicleId(10L)
                .plateNumber(plate)
                .eventType(eventType)
                .description("Sample description")
                .actorName("Test Actor")
                .actorRole("FIELD_STAFF")
                .occurredAt(occurredAt)
                .build();
    }
}
