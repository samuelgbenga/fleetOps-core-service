package com.fleetops.core.module.company.dto;

import com.fleetops.core.module.company.model.Company;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CompanyResponse {
    private Long id;
    private String name;
    private String email;
    private String contactPhone;
    private String address;
    private String logoUrl;
    private String status;
    private String rejectionReason;
    private LocalDateTime registeredAt;
    private LocalDateTime approvedAt;

    public static CompanyResponse from(Company c) {
        return CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .contactPhone(c.getContactPhone())
                .address(c.getAddress())
                .logoUrl(c.getLogoUrl())
                .status(c.getStatus().name())
                .rejectionReason(c.getRejectionReason())
                .registeredAt(c.getRegisteredAt())
                .approvedAt(c.getApprovedAt())
                .build();
    }
}
