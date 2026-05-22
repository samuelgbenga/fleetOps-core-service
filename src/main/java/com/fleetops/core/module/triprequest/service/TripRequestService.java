package com.fleetops.core.module.triprequest.service;

import com.fleetops.core.module.triprequest.dto.RejectTripRequest;
import com.fleetops.core.module.triprequest.dto.TripRequestCreate;
import com.fleetops.core.module.triprequest.dto.TripRequestResponse;

import java.util.List;

public interface TripRequestService {
    TripRequestResponse create(TripRequestCreate request);
    List<TripRequestResponse> getAll();
    List<TripRequestResponse> getMy();
    List<TripRequestResponse> getMyApproved();
    TripRequestResponse approve(Long id);
    TripRequestResponse reject(Long id, RejectTripRequest request);
    TripRequestResponse complete(Long id);
    TripRequestResponse confirmCompletion(Long id);
    TripRequestResponse cancel(Long id);
    TripRequestResponse approveCancellation(Long id);
    void autoRejectStaleRequests();
}
