package com.fleetops.core.module.company.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Column(name = "logo_url")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @PrePersist
    protected void onCreate() {
        this.registeredAt = LocalDateTime.now();
    }
}
