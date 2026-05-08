package com.fleetops.core.mileage.entity;

import com.fleetops.core.user.entity.User;
import com.fleetops.core.vehicle.entity.Vehicle;
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
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(name = "mileage_added", nullable = false)
    private Double mileageAdded;

    @Column(name = "mileage_after", nullable = false)
    private Double mileageAfter;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    protected void onCreate() {
        this.loggedAt = LocalDateTime.now();
    }
}
