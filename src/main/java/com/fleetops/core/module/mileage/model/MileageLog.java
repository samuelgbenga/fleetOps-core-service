package com.fleetops.core.module.mileage.model;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.vehicle.model.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mileage_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MileageLog {

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
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_request_id")
    private TripRequest tripRequest;

    @Column(name = "reported_mileage", nullable = false)
    private Double reportedMileage;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    protected void onCreate() {
        this.loggedAt = LocalDateTime.now();
    }
}
