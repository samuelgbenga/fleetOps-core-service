package com.fleetops.core.module.breakdown.model;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.maintenance.model.MaintenanceFlag;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.vehicle.model.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "breakdown_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakdownReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_request_id")
    private TripRequest tripRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_staff_id", nullable = false)
    private User fieldStaff;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "location_description", columnDefinition = "TEXT")
    private String locationDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BreakdownStatus status = BreakdownStatus.REPORTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_crew_id")
    private User assignedCrew;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_vehicle_id")
    private Vehicle replacementVehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_flag_id")
    private MaintenanceFlag maintenanceFlag;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        this.reportedAt = LocalDateTime.now();
    }
}
