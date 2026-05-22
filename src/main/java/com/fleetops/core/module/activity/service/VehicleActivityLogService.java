package com.fleetops.core.module.activity.service;

import com.fleetops.core.module.activity.dto.VehicleActivityLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VehicleActivityLogService {

    Page<VehicleActivityLogResponse> getLogs(Pageable pageable);

    Page<VehicleActivityLogResponse> getAllLogs(Pageable pageable);
}
