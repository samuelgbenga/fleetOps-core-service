package com.fleetops.core.activity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_activity_logs", indexes = {
        @Index(name = "idx_activity_plate", columnList = "plate_number"),
        @Index(name = "idx_activity_occurred_at", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "plate_number", nullable = false, length = 10)
    private String plateNumber;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
