package com.fleetops.core.module.company.dto;

import lombok.Data;

@Data
public class CompanyUpdateRequest {
    private String contactPhone;
    private String address;
    private String logoUrl;
}
