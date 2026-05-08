package com.fleetops.core.vehicle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "fleet_manager_name", nullable = false)
    private String fleetManagerName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes;

    @Column(name = "new_milestone_interval", nullable = false)
    private Double newMilestoneInterval;

    @Column(name = "serviced_at", nullable = false, updatable = false)
    private LocalDateTime servicedAt;

    @PrePersist
    protected void onCreate() {
        this.servicedAt = LocalDateTime.now();
    }
}
