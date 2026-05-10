package com.fleetops.core.vehicle.entity;

import com.fleetops.core.media.entity.Media;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import jakarta.persistence.*;
import lombok.*;

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
    @Builder.Default
    private Double milestoneInterval = 3000.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "vehicle_media",
            joinColumns = @JoinColumn(name = "vehicle_id"),
            inverseJoinColumns = @JoinColumn(name = "media_id", unique = true)
    )
    @Builder.Default
    private List<Media> mediaFiles = new ArrayList<>();

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

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
