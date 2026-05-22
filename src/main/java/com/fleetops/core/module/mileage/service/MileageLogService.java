package com.fleetops.core.module.mileage.service;

import com.fleetops.core.module.mileage.dto.MileageLogRequest;
import com.fleetops.core.module.mileage.dto.MileageLogResponse;

import java.util.List;

public interface MileageLogService {
    MileageLogResponse submit(MileageLogRequest request);
    List<MileageLogResponse> getByVehicle(Long vehicleId);
}
