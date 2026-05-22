package com.fleetops.core.module.breakdown.service;

import com.fleetops.core.module.breakdown.dto.BreakdownReportRequest;
import com.fleetops.core.module.breakdown.dto.BreakdownResponse;
import com.fleetops.core.module.breakdown.dto.DispatchCrewRequest;
import com.fleetops.core.module.breakdown.dto.DispatchReplacementRequest;

import java.util.List;

public interface BreakdownService {

    BreakdownResponse report(BreakdownReportRequest request);

    BreakdownResponse dispatchReplacement(Long id, DispatchReplacementRequest request);

    BreakdownResponse dispatchCrew(Long id, DispatchCrewRequest request);

    BreakdownResponse resolve(Long id);

    BreakdownResponse getById(Long id);

    List<BreakdownResponse> getAll();

    List<BreakdownResponse> getAllPlatform();
}
