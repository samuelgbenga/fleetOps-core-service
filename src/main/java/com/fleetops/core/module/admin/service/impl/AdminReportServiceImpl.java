package com.fleetops.core.module.admin.service.impl;

import com.fleetops.core.module.admin.dto.PlatformDashboardSummaryResponse;
import com.fleetops.core.module.admin.dto.UtilisationReportResponse;
import com.fleetops.core.module.admin.dto.VehicleHealthReportResponse;
import com.fleetops.core.module.admin.service.AdminReportService;
import com.fleetops.core.module.breakdown.repository.BreakdownRepository;
import com.fleetops.core.module.company.model.CompanyStatus;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private final CompanyRepository companyRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final TripRequestRepository tripRequestRepository;
    private final BreakdownRepository breakdownRepository;

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardSummaryResponse getPlatformDashboardSummary() {
        long totalCompanies = companyRepository.count();
        long activeCompanies = companyRepository.findAllByStatus(CompanyStatus.APPROVED).size();
        long pendingCompanies = companyRepository.findAllByStatus(CompanyStatus.PENDING).size();
        long totalVehicles = vehicleRepository.count();
        long totalUsers = userRepository.count();
        long totalOpenFlags = maintenanceFlagRepository.findByStatus(FlagStatus.OPEN).size();
        long totalBreakdowns = breakdownRepository.count();
        long totalResolvedFlags = maintenanceFlagRepository.findByStatus(FlagStatus.RESOLVED).size();

        return PlatformDashboardSummaryResponse.builder()
                .totalCompanies(totalCompanies)
                .activeCompanies(activeCompanies)
                .pendingCompanies(pendingCompanies)
                .totalVehicles(totalVehicles)
                .totalUsers(totalUsers)
                .totalOpenFlags(totalOpenFlags)
                .totalBreakdowns(totalBreakdowns)
                .totalResolvedFlags(totalResolvedFlags)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UtilisationReportResponse getUtilisationReport() {
        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        List<Vehicle> vehicles = vehicleRepository.findByCompanyId(companyId);
        long total = vehicles.size();
        long available = vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.AVAILABLE).count();
        long assigned = vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.ASSIGNED).count();
        long maintenance = vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.MAINTENANCE).count();
        long totalTrips = tripRequestRepository.countByCompanyIdAndStatus(companyId, TripRequestStatus.COMPLETED)
                + tripRequestRepository.countByCompanyIdAndStatus(companyId, TripRequestStatus.PENDING)
                + tripRequestRepository.countByCompanyIdAndStatus(companyId, TripRequestStatus.APPROVED)
                + tripRequestRepository.countByCompanyIdAndStatus(companyId, TripRequestStatus.REJECTED);
        long completedTrips = tripRequestRepository.countByCompanyIdAndStatus(companyId, TripRequestStatus.COMPLETED);
        double utilizationRate = total > 0 ? (double) assigned / total * 100 : 0;

        return UtilisationReportResponse.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .totalVehicles(total)
                .availableVehicles(available)
                .assignedVehicles(assigned)
                .maintenanceVehicles(maintenance)
                .totalTrips(totalTrips)
                .completedTrips(completedTrips)
                .utilizationRate(Math.round(utilizationRate * 100.0) / 100.0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleHealthReportResponse getVehicleHealthReport() {
        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        List<Vehicle> vehicles = vehicleRepository.findByCompanyId(companyId);
        long total = vehicles.size();
        double avgHealth = vehicles.stream()
                .filter(v -> v.getHealthScore() != null)
                .mapToDouble(Vehicle::getHealthScore)
                .average()
                .orElse(0.0);

        long critical = vehicles.stream()
                .filter(v -> v.getHealthScore() != null && v.getHealthScore() < 31)
                .count();
        long poor = vehicles.stream()
                .filter(v -> v.getHealthScore() != null && v.getHealthScore() >= 31 && v.getHealthScore() < 51)
                .count();
        long fair = vehicles.stream()
                .filter(v -> v.getHealthScore() != null && v.getHealthScore() >= 51 && v.getHealthScore() < 71)
                .count();
        long good = vehicles.stream()
                .filter(v -> v.getHealthScore() != null && v.getHealthScore() >= 71 && v.getHealthScore() < 86)
                .count();
        long excellent = vehicles.stream()
                .filter(v -> v.getHealthScore() != null && v.getHealthScore() >= 86)
                .count();
        long markedForSale = vehicles.stream()
                .filter(v -> Boolean.TRUE.equals(v.getMarkedForSale()))
                .count();

        return VehicleHealthReportResponse.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .totalVehicles(total)
                .avgHealthScore(Math.round(avgHealth * 100.0) / 100.0)
                .criticalCount(critical)
                .poorCount(poor)
                .fairCount(fair)
                .goodCount(good)
                .excellentCount(excellent)
                .markedForSaleCount(markedForSale)
                .build();
    }
}
