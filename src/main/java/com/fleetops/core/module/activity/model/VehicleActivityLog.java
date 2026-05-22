package com.fleetops.core.module.activity.model;

import com.fleetops.core.module.company.model.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
