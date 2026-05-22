package com.fleetops.core.module.activity.service.impl;

import com.fleetops.core.module.activity.dto.VehicleActivityLogResponse;
import com.fleetops.core.module.activity.repository.VehicleActivityLogRepository;
import com.fleetops.core.module.activity.service.VehicleActivityLogService;
import com.fleetops.core.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleActivityLogServiceImpl implements VehicleActivityLogService {

    private final VehicleActivityLogRepository activityLogRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<VehicleActivityLogResponse> getLogs(Pageable pageable) {
        Long companyId = TenantContext.getCompanyId();
        return activityLogRepository.findByCompanyId(companyId, pageable)
                .map(VehicleActivityLogResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VehicleActivityLogResponse> getAllLogs(Pageable pageable) {
        return activityLogRepository.findAll(pageable)
                .map(VehicleActivityLogResponse::from);
    }
}
