package com.fleetops.core.module.maintenance.model;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flag_id", nullable = false)
    private MaintenanceFlag flag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id", nullable = false)
    private User crew;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_by_user_id", nullable = false)
    private User ratedByUser;

    @Column(nullable = false)
    private Integer stars;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "rated_at", nullable = false, updatable = false)
    private LocalDateTime ratedAt;

    @PrePersist
    protected void onCreate() {
        this.ratedAt = LocalDateTime.now();
    }
}
