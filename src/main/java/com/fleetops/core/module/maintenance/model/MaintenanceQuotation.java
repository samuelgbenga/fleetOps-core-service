package com.fleetops.core.module.maintenance.model;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_quotations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceQuotation {

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

    @Column(name = "estimated_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedCost;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "parts_needed", columnDefinition = "TEXT")
    private String partsNeeded;

    @Column(name = "actual_cost", precision = 15, scale = 2)
    private BigDecimal actualCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private QuotationStatus status = QuotationStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "revision_number", nullable = false)
    @Builder.Default
    private Integer revisionNumber = 1;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }
}
