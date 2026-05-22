package com.fleetops.core.module.vehicle.dto;

import com.fleetops.core.module.media.dto.MediaResponse;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleImage;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class VehicleResponse {
    private Long id;
    private Long companyId;
    private String make;
    private String model;
    private String plateNumber;
    private Double currentMileage;
    private Double milestoneInterval;
    private BigDecimal purchasePrice;
    private LocalDate purchaseDate;
    private BigDecimal totalMaintenanceSpend;
    private Integer breakdownCount;
    private Double healthScore;
    private String healthGrade;
    private String sellRecommendation;
    private Double lifecyclePercentage;
    private Boolean markedForSale;
    private String status;
    private LocalDateTime registeredAt;
    private List<MediaResponse> images;

    public static VehicleResponse from(Vehicle v, List<VehicleImage> images) {
        return VehicleResponse.builder()
                .id(v.getId())
                .companyId(v.getCompany().getId())
                .make(v.getMake())
                .model(v.getModel())
                .plateNumber(v.getPlateNumber())
                .currentMileage(v.getCurrentMileage())
                .milestoneInterval(v.getMilestoneInterval())
                .purchasePrice(v.getPurchasePrice())
                .purchaseDate(v.getPurchaseDate())
                .totalMaintenanceSpend(v.getTotalMaintenanceSpend())
                .breakdownCount(v.getBreakdownCount())
                .healthScore(v.getHealthScore())
                .healthGrade(v.getHealthGrade())
                .sellRecommendation(v.getSellRecommendation())
                .lifecyclePercentage(v.getLifecyclePercentage())
                .markedForSale(v.getMarkedForSale())
                .status(v.getStatus().name())
                .registeredAt(v.getRegisteredAt())
                .images(images.stream()
                        .map(img -> MediaResponse.builder()
                                .publicId(img.getImageId())
                                .url(img.getImageUrl())
                                .ownerType("VEHICLE")
                                .ownerId(v.getId())
                                .build())
                        .toList())
                .build();
    }
}
