package com.fleetops.core.module.vehicle.service.impl;

import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.maintenance.model.FlagStatus;
import com.fleetops.core.module.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.module.mileage.repository.MileageLogRepository;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.vehicle.dto.MilestoneIntervalRequest;
import com.fleetops.core.module.vehicle.dto.VehicleImageRequest;
import com.fleetops.core.module.vehicle.dto.VehicleImageResponse;
import com.fleetops.core.module.vehicle.dto.VehicleRequest;
import com.fleetops.core.module.vehicle.dto.VehicleResponse;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleImage;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleImageRepository;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.module.vehicle.service.VehicleService;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CompanyRepository companyRepository;
    private final MileageLogRepository mileageLogRepository;
    private final MaintenanceFlagRepository maintenanceFlagRepository;
    private final TripRequestRepository tripRequestRepository;
    private final VehicleImageRepository vehicleImageRepository;

    @Value("${app.vehicle.default-milestone-interval}")
    private Double defaultMilestoneInterval;

    @Override
    @Transactional
    public VehicleResponse registerVehicle(VehicleRequest request) {
        String plateNumber = request.getPlateNumber().trim().toUpperCase();
        if (vehicleRepository.existsByPlateNumber(plateNumber)) {
            throw new ConflictException("Plate number already registered: " + plateNumber);
        }

        Long companyId = TenantContext.getCompanyId();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Vehicle vehicle = Vehicle.builder()
                .company(company)
                .make(request.getMake())
                .model(request.getModel())
                .plateNumber(plateNumber)
                .milestoneInterval(request.getMilestoneInterval() != null
                        ? request.getMilestoneInterval() : defaultMilestoneInterval)
                .purchasePrice(request.getPurchasePrice())
                .purchaseDate(request.getPurchaseDate())
                .build();

        try {
            return toResponse(vehicleRepository.save(vehicle));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Plate number already registered: " + plateNumber);
        }
    }

    @Override
    public List<VehicleResponse> getAllVehicles() {
        if (TenantContext.isPlatformLevel()) {
            return toResponseList(vehicleRepository.findAll());
        }
        return toResponseList(vehicleRepository.findByCompanyId(TenantContext.getCompanyId()));
    }

    @Override
    public List<VehicleResponse> getAvailableVehicles() {
        return toResponseList(vehicleRepository.findByCompanyIdAndStatus(
                TenantContext.getCompanyId(), VehicleStatus.AVAILABLE));
    }

    @Override
    public List<VehicleResponse> getMyTripVehicles() {
        Long userId = TenantContext.getUserId();
        List<Vehicle> vehicles = tripRequestRepository
                .findByRequestedByIdAndStatusIn(userId,
                        List.of(TripRequestStatus.APPROVED, TripRequestStatus.BROKEN_DOWN))
                .stream()
                .map(t -> t.getVehicle())
                .collect(Collectors.toMap(Vehicle::getId, v -> v, (a, b) -> a))
                .values()
                .stream()
                .toList();
        return toResponseList(vehicles);
    }

    @Override
    public VehicleResponse getVehicleById(Long id) {
        Vehicle v = vehicleRepository.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        return toResponse(v);
    }

    @Override
    @Transactional
    public VehicleResponse updateMilestoneInterval(Long id, MilestoneIntervalRequest request) {
        Vehicle v = vehicleRepository.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        v.setMilestoneInterval(request.getMilestoneInterval());
        return toResponse(vehicleRepository.save(v));
    }

    @Override
    @Transactional
    public VehicleImageResponse uploadVehicleImage(Long vehicleId, VehicleImageRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        VehicleImage saved = vehicleImageRepository.save(VehicleImage.builder()
                .vehicle(vehicle)
                .imageUrl(request.getImageUrl())
                .imageId(request.getImageId())
                .build());
        return VehicleImageResponse.from(saved);
    }

    @Override
    @Transactional
    public List<VehicleImageResponse> uploadBulkVehicleImages(Long vehicleId, List<VehicleImageRequest> requests) {
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        return requests.stream().map(req -> {
            VehicleImage saved = vehicleImageRepository.save(VehicleImage.builder()
                    .vehicle(vehicle)
                    .imageUrl(req.getImageUrl())
                    .imageId(req.getImageId())
                    .build());
            return VehicleImageResponse.from(saved);
        }).toList();
    }

    @Override
    @Transactional
    public void deleteVehicleImage(Long vehicleId, String imageId) {
        vehicleRepository.findByIdAndCompanyId(vehicleId, TenantContext.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        VehicleImage image = vehicleImageRepository.findByImageId(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        vehicleImageRepository.delete(image);
    }

    @Override
    @Transactional
    public void recomputeHealthForVehicle(Vehicle vehicle) {
        double mileageWear = Math.min(vehicle.getCurrentMileage() / vehicle.getMaxMileage(), 1.0);

        long resolvedFlags = maintenanceFlagRepository
                .countByVehicleIdAndStatus(vehicle.getId(), FlagStatus.RESOLVED);
        double maintFreq = Math.min((double) resolvedFlags / vehicle.getMaxMaintenanceRounds(), 1.0);

        double costRatio = vehicle.getPurchasePrice() != null && vehicle.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0
                ? Math.min(vehicle.getTotalMaintenanceSpend().doubleValue()
                / vehicle.getPurchasePrice().doubleValue(), 1.0)
                : 0.0;

        long qualifiedTrips = mileageLogRepository.countQualifiedTripsByVehicleId(vehicle.getId());
        double tripIntensity = Math.min((double) qualifiedTrips / vehicle.getMaxTrips(), 1.0);

        double breakdownRate = Math.min(vehicle.getBreakdownCount() / 5.0, 1.0);

        double ageRatio = 0.0;
        if (vehicle.getPurchaseDate() != null) {
            double years = vehicle.getPurchaseDate().until(LocalDate.now()).getYears();
            ageRatio = Math.min(years / 15.0, 1.0);
        }

        double wearIndex = (mileageWear * 25) + (maintFreq * 20) + (costRatio * 15)
                + (tripIntensity * 15) + (breakdownRate * 15) + (ageRatio * 10);
        double healthScore = 100 - wearIndex;

        String grade;
        if (healthScore >= 86) grade = "EXCELLENT";
        else if (healthScore >= 71) grade = "GOOD";
        else if (healthScore >= 51) grade = "FAIR";
        else if (healthScore >= 31) grade = "POOR";
        else grade = "CRITICAL";

        List<String> advisories = new ArrayList<>();
        if (healthScore < 30) advisories.add("Immediate disposal recommended");
        if (vehicle.getPurchasePrice() != null
                && vehicle.getTotalMaintenanceSpend().doubleValue()
                > 0.7 * vehicle.getPurchasePrice().doubleValue()) {
            advisories.add("Maintenance cost exceeds 70% of purchase value");
        }
        if (vehicle.getBreakdownCount() >= 3 && healthScore < 50) {
            advisories.add("High breakdown frequency — consider replacement");
        }
        if (vehicle.getPurchaseDate() != null) {
            double years = vehicle.getPurchaseDate().until(LocalDate.now()).getYears();
            if (years > 10 && healthScore < 60) {
                advisories.add("Age-adjusted advisory: plan replacement within 6 months");
            }
        }
        double mileageFactor = Math.min(vehicle.getCurrentMileage() / vehicle.getMaxMileage(), 1.0);
        double tripFactor = Math.min((double) qualifiedTrips / vehicle.getMaxTrips(), 1.0);
        double maintFactor = Math.min((double) resolvedFlags / vehicle.getMaxMaintenanceRounds(), 1.0);
        double lifecycle = Math.min(100.0,
                (mileageFactor * 0.5 + tripFactor * 0.25 + maintFactor * 0.25) * 100);
        if (lifecycle >= 80) advisories.add("Lifecycle threshold reached — vehicle flagged for sale");

        vehicle.setHealthScore(healthScore);
        vehicle.setHealthGrade(grade);
        vehicle.setSellRecommendation(advisories.isEmpty() ? null : String.join("; ", advisories));
        vehicle.setHealthComputedAt(LocalDateTime.now());
        vehicle.setLifecyclePercentage(lifecycle);

        if (lifecycle >= 80.0 && vehicle.getStatus() != VehicleStatus.OUT_OF_SERVICE) {
            vehicle.setStatus(VehicleStatus.OUT_OF_SERVICE);
            vehicle.setMarkedForSale(true);
        }
    }

    private VehicleResponse toResponse(Vehicle v) {
        return VehicleResponse.from(v, vehicleImageRepository.findByVehicleId(v.getId()));
    }

    private List<VehicleResponse> toResponseList(List<Vehicle> vehicles) {
        if (vehicles.isEmpty()) return List.of();
        List<Long> ids = vehicles.stream().map(Vehicle::getId).toList();
        Map<Long, List<VehicleImage>> imagesByVehicle =
                vehicleImageRepository.findByVehicleIdIn(ids)
                        .stream()
                        .collect(Collectors.groupingBy(img -> img.getVehicle().getId()));
        return vehicles.stream()
                .map(v -> VehicleResponse.from(v, imagesByVehicle.getOrDefault(v.getId(), List.of())))
                .toList();
    }
}
