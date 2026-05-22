
package com.fleetops.core.module.vehicle.model;

import com.fleetops.core.module.company.model.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(name = "plate_number", nullable = false, unique = true)
    private String plateNumber;

    @Column(name = "current_mileage", nullable = false)
    @Builder.Default
    private Double currentMileage = 0.0;

    @Column(name = "milestone_interval", nullable = false)
    private Double milestoneInterval;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "total_maintenance_spend", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalMaintenanceSpend = BigDecimal.ZERO;

    @Column(name = "breakdown_count", nullable = false)
    @Builder.Default
    private Integer breakdownCount = 0;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "health_grade")
    private String healthGrade;

    @Column(name = "sell_recommendation", columnDefinition = "TEXT")
    private String sellRecommendation;

    @Column(name = "health_computed_at")
    private LocalDateTime healthComputedAt;

    @Column(name = "lifecycle_percentage")
    @Builder.Default
    private Double lifecyclePercentage = 0.0;

    @Column(name = "marked_for_sale")
    @Builder.Default
    private Boolean markedForSale = false;

    @Column(name = "max_mileage")
    @Builder.Default
    private Double maxMileage = 300000.0;

    @Column(name = "max_trips")
    @Builder.Default
    private Integer maxTrips = 500;

    @Column(name = "max_maintenance_rounds")
    @Builder.Default
    private Integer maxMaintenanceRounds = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<VehicleImage> images = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.registeredAt = LocalDateTime.now();
    }

    public boolean isAvailable() {
        return this.status == VehicleStatus.AVAILABLE;
    }

    public boolean isMilestoneReached(Double oldMileage) {
        return Math.floor(this.currentMileage / this.milestoneInterval)
                > Math.floor(oldMileage / this.milestoneInterval);
    }
}
