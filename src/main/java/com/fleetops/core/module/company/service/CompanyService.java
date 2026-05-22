package com.fleetops.core.module.company.service;

import com.fleetops.core.module.company.dto.*;

import java.util.List;

public interface CompanyService {
    CompanyResponse register(CompanyRegistrationRequest request);
    CompanyResponse getMyProfile();
    CompanyResponse updateProfile(CompanyUpdateRequest request);
    List<CompanyResponse> getAllCompanies();
    CompanyResponse getCompanyById(Long id);
    CompanyResponse approveCompany(Long id);
    CompanyResponse rejectCompany(Long id, RejectionRequest request);
    CompanyResponse suspendCompany(Long id);
    CompanyResponse reactivateCompany(Long id);
}
