package com.fleetops.core.module.maintenance.service;

import com.fleetops.core.module.maintenance.dto.*;

import java.util.List;

public interface MaintenanceFlagService {
    MaintenanceFlagResponse createFlag(CreateFlagRequest request);
    List<MaintenanceFlagResponse> getAll();
    MaintenanceFlagResponse getById(Long id);
    List<MaintenanceFlagResponse> getByVehicle(Long vehicleId);
    List<MaintenanceFlagResponse> getMyCrew();
    MaintenanceFlagResponse assignCrew(Long flagId, AssignCrewRequest request);
    QuotationResponse submitQuotation(Long flagId, QuotationRequest request);
    QuotationResponse reviseQuotation(Long flagId, QuotationRequest request);
    MaintenanceFlagResponse approveQuotation(Long flagId);
    MaintenanceFlagResponse rejectQuotation(Long flagId, RejectQuotationRequest request);
    MaintenanceFlagResponse updateProgress(Long flagId, ProgressUpdateRequest request);
    MaintenanceFlagResponse markDone(Long flagId);
    MaintenanceFlagResponse resolve(Long flagId, RatingRequest request);
    MessageResponse sendMessage(Long flagId, MessageRequest request);
    List<MessageResponse> getMessages(Long flagId);
    List<MaintenanceFlagResponse> getAllPlatform();
}
