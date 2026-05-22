package com.fleetops.core.module.activity.service;

import com.fleetops.core.module.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.module.activity.model.VehicleActivityLog;
import com.fleetops.core.module.activity.repository.VehicleActivityLogRepository;
import com.fleetops.core.module.activity.service.impl.VehicleActivityLogServiceImpl;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.shared.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleActivityLogServiceImplTest {

    @Mock
    private VehicleActivityLogRepository activityLogRepository;

    @InjectMocks
    private VehicleActivityLogServiceImpl activityLogService;

    private static final Long COMPANY_ID = 10L;
    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, 1L, "FLEET_MANAGER", "COMPANY");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private VehicleActivityLog buildLog(Long id) {
        Company company = Company.builder().id(COMPANY_ID).name("Test Corp").email("test@corp.com").build();
        return VehicleActivityLog.builder()
                .id(id)
                .company(company)
                .vehicleId(200L)
                .plateNumber("ABC-123-XY")
                .eventType("TRIP_COMPLETED")
                .description("Trip was completed successfully")
                .actorName("John Doe")
                .actorRole("FLEET_MANAGER")
                .occurredAt(LocalDateTime.of(2024, 6, 15, 10, 30))
                .build();
    }

    // ========================= getLogs =========================

    @Test
    void getLogs_returnsPageFromRepository() {
        Page<VehicleActivityLog> page = new PageImpl<>(List.of(buildLog(1L)), PAGEABLE, 1);
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE)).thenReturn(page);

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getLogs_usesCompanyIdFromTenantContext() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE)).thenReturn(Page.empty());

        activityLogService.getLogs(PAGEABLE);

        verify(activityLogRepository).findByCompanyId(COMPANY_ID, PAGEABLE);
    }

    @Test
    void getLogs_mapsIdCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(42L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getId()).isEqualTo(42L);
    }

    @Test
    void getLogs_mapsCompanyIdFromNestedCompanyObject() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void getLogs_mapsVehicleIdCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getVehicleId()).isEqualTo(200L);
    }

    @Test
    void getLogs_mapsPlateNumberCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getPlateNumber()).isEqualTo("ABC-123-XY");
    }

    @Test
    void getLogs_mapsEventTypeCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getEventType()).isEqualTo("TRIP_COMPLETED");
    }

    @Test
    void getLogs_mapsDescriptionCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Trip was completed successfully");
    }

    @Test
    void getLogs_mapsActorNameCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getActorName()).isEqualTo("John Doe");
    }

    @Test
    void getLogs_mapsActorRoleCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getActorRole()).isEqualTo("FLEET_MANAGER");
    }

    @Test
    void getLogs_mapsOccurredAtCorrectly() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getOccurredAt())
                .isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30));
    }

    @Test
    void getLogs_returnsEmptyPageWhenRepositoryReturnsEmpty() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE)).thenReturn(Page.empty());

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getLogs_passesCustomPageableToRepository() {
        Pageable custom = PageRequest.of(2, 5, Sort.by("occurredAt").descending());
        when(activityLogRepository.findByCompanyId(COMPANY_ID, custom)).thenReturn(Page.empty());

        activityLogService.getLogs(custom);

        verify(activityLogRepository).findByCompanyId(COMPANY_ID, custom);
    }

    @Test
    void getLogs_doesNotCallFindAll() {
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE)).thenReturn(Page.empty());

        activityLogService.getLogs(PAGEABLE);

        verify(activityLogRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getLogs_returnsCorrectTotalElements() {
        List<VehicleActivityLog> logs = List.of(buildLog(1L), buildLog(2L), buildLog(3L));
        Page<VehicleActivityLog> page = new PageImpl<>(logs, PAGEABLE, 50L);
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE)).thenReturn(page);

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getTotalElements()).isEqualTo(50L);
    }

    @Test
    void getLogs_multipleItemsAllMapped() {
        List<VehicleActivityLog> logs = List.of(buildLog(1L), buildLog(2L));
        when(activityLogRepository.findByCompanyId(COMPANY_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(logs));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(PAGEABLE);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(1).getId()).isEqualTo(2L);
    }

    @Test
    void getLogs_usesDifferentCompanyIdFromTenantContext() {
        TenantContext.set(999L, 1L, "FLEET_MANAGER", "COMPANY");
        when(activityLogRepository.findByCompanyId(999L, PAGEABLE)).thenReturn(Page.empty());

        activityLogService.getLogs(PAGEABLE);

        verify(activityLogRepository).findByCompanyId(999L, PAGEABLE);
        verify(activityLogRepository, never()).findByCompanyId(COMPANY_ID, PAGEABLE);
    }

    @Test
    void getLogs_pageNumberAndSizePreserved() {
        Pageable page2 = PageRequest.of(1, 10);
        when(activityLogRepository.findByCompanyId(COMPANY_ID, page2))
                .thenReturn(new PageImpl<>(List.of(), page2, 0));

        Page<VehicleActivityLogResponse> result = activityLogService.getLogs(page2);

        assertThat(result.getNumber()).isEqualTo(1);
    }

    // ========================= getAllLogs =========================

    @Test
    void getAllLogs_returnsPageFromRepository() {
        Page<VehicleActivityLog> page = new PageImpl<>(List.of(buildLog(1L)), PAGEABLE, 1);
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(page);

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAllLogs_neverCallsFindByCompanyId() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(Page.empty());

        activityLogService.getAllLogs(PAGEABLE);

        verify(activityLogRepository, never()).findByCompanyId(any(), any());
    }

    @Test
    void getAllLogs_callsFindAllWithPageable() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(Page.empty());

        activityLogService.getAllLogs(PAGEABLE);

        verify(activityLogRepository).findAll(PAGEABLE);
    }

    @Test
    void getAllLogs_mapsIdCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(99L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getId()).isEqualTo(99L);
    }

    @Test
    void getAllLogs_mapsCompanyIdFromNestedCompanyObject() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void getAllLogs_mapsVehicleIdCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getVehicleId()).isEqualTo(200L);
    }

    @Test
    void getAllLogs_mapsPlateNumberCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getPlateNumber()).isEqualTo("ABC-123-XY");
    }

    @Test
    void getAllLogs_mapsEventTypeCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getEventType()).isEqualTo("TRIP_COMPLETED");
    }

    @Test
    void getAllLogs_mapsDescriptionCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Trip was completed successfully");
    }

    @Test
    void getAllLogs_mapsActorNameCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getActorName()).isEqualTo("John Doe");
    }

    @Test
    void getAllLogs_mapsActorRoleCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getActorRole()).isEqualTo("FLEET_MANAGER");
    }

    @Test
    void getAllLogs_mapsOccurredAtCorrectly() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(buildLog(1L))));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent().get(0).getOccurredAt())
                .isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30));
    }

    @Test
    void getAllLogs_returnsEmptyPageWhenRepositoryReturnsEmpty() {
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(Page.empty());

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getAllLogs_passesCustomPageableToRepository() {
        Pageable custom = PageRequest.of(3, 10, Sort.by("occurredAt").ascending());
        when(activityLogRepository.findAll(custom)).thenReturn(Page.empty());

        activityLogService.getAllLogs(custom);

        verify(activityLogRepository).findAll(custom);
    }

    @Test
    void getAllLogs_returnsCorrectTotalElements() {
        List<VehicleActivityLog> logs = List.of(buildLog(1L), buildLog(2L));
        Page<VehicleActivityLog> page = new PageImpl<>(logs, PAGEABLE, 100L);
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(page);

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getTotalElements()).isEqualTo(100L);
    }

    @Test
    void getAllLogs_multipleItemsMappedInOrder() {
        List<VehicleActivityLog> logs = List.of(buildLog(10L), buildLog(20L), buildLog(30L));
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(logs));

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(VehicleActivityLogResponse::getId)
                .containsExactly(10L, 20L, 30L);
    }

    @Test
    void getAllLogs_worksWhenTenantContextIsCleared() {
        TenantContext.clear();
        when(activityLogRepository.findAll(PAGEABLE)).thenReturn(Page.empty());

        Page<VehicleActivityLogResponse> result = activityLogService.getAllLogs(PAGEABLE);

        assertThat(result).isNotNull();
        verify(activityLogRepository, never()).findByCompanyId(any(), any());
    }
}
