package com.fleetops.core.module.triprequest.model;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.vehicle.model.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_request_id", nullable = false)
    private TripRequest tripRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id", nullable = false)
    private User assignedTo;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = LocalDateTime.now();
    }
}
