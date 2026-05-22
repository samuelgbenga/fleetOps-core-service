package com.fleetops.core.module.maintenance.model;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.vehicle.model.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceFlag {

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
    @JoinColumn(name = "assigned_crew_id")
    private User assignedCrew;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FlagStatus status = FlagStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "progress_notes", columnDefinition = "TEXT")
    private String progressNotes;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        this.openedAt = LocalDateTime.now();
    }
}
