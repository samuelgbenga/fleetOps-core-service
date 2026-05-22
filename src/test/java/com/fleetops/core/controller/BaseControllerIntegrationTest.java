package com.fleetops.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetops.core.module.admin.service.AdminReportService;
import com.fleetops.core.module.activity.service.VehicleActivityLogService;
import com.fleetops.core.module.auth.service.AuthService;
import com.fleetops.core.module.auth.util.JwtUtil;
import com.fleetops.core.module.breakdown.service.BreakdownService;
import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.service.CompanyService;
import com.fleetops.core.module.maintenance.service.MaintenanceFlagService;
import com.fleetops.core.module.media.service.MediaService;
import com.fleetops.core.module.mileage.service.MileageLogService;
import com.fleetops.core.module.outbox.service.OutboxService;
import com.fleetops.core.module.triprequest.service.TripRequestService;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.service.UserService;
import com.fleetops.core.module.vehicle.service.VehicleLifecyclePdfService;
import com.fleetops.core.module.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseControllerIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtUtil jwtUtil;

    @MockBean
    protected UserDetailsService userDetailsService;

    @MockBean
    protected AuthService authService;
    @MockBean
    protected CompanyService companyService;
    @MockBean
    protected VehicleService vehicleService;
    @MockBean
    protected VehicleLifecyclePdfService vehicleLifecyclePdfService;
    @MockBean
    protected TripRequestService tripRequestService;
    @MockBean
    protected MileageLogService mileageLogService;
    @MockBean
    protected MaintenanceFlagService maintenanceFlagService;
    @MockBean
    protected BreakdownService breakdownService;
    @MockBean
    protected VehicleActivityLogService vehicleActivityLogService;
    @MockBean
    protected AdminReportService adminReportService;
    @MockBean
    protected UserService userService;
    @MockBean
    protected MediaService mediaService;
    @MockBean
    protected OutboxService outboxService;

    protected static final Long COMPANY_ID = 1L;

    protected User companyAdmin;
    protected User fleetManager;
    protected User fieldStaff;
    protected User maintenanceCrew;
    protected User platformAdmin;

    protected String companyAdminToken;
    protected String fleetManagerToken;
    protected String fieldStaffToken;
    protected String maintenanceCrewToken;
    protected String platformAdminToken;

    @BeforeEach
    void setUpBase() {
        Company company = Company.builder()
                .id(COMPANY_ID)
                .name("Test Co")
                .email("test@co.com")
                .build();

        companyAdmin = User.builder()
                .id(10L).email("admin@co.com").name("Admin User").password("hashed")
                .role(Role.COMPANY_ADMIN).userType(UserType.COMPANY).company(company).build();

        fleetManager = User.builder()
                .id(11L).email("fm@co.com").name("Fleet Manager").password("hashed")
                .role(Role.FLEET_MANAGER).userType(UserType.COMPANY).company(company).build();

        fieldStaff = User.builder()
                .id(12L).email("staff@co.com").name("Field Staff").password("hashed")
                .role(Role.FIELD_STAFF).userType(UserType.COMPANY).company(company).build();

        maintenanceCrew = User.builder()
                .id(13L).email("crew@co.com").name("Maintenance Crew").password("hashed")
                .role(Role.MAINTENANCE_CREW).userType(UserType.COMPANY).company(company).build();

        platformAdmin = User.builder()
                .id(99L).email("platform@admin.com").name("Platform Admin").password("hashed")
                .role(Role.PLATFORM_ADMIN).userType(UserType.PLATFORM).build();

        when(userDetailsService.loadUserByUsername("admin@co.com")).thenReturn(companyAdmin);
        when(userDetailsService.loadUserByUsername("fm@co.com")).thenReturn(fleetManager);
        when(userDetailsService.loadUserByUsername("staff@co.com")).thenReturn(fieldStaff);
        when(userDetailsService.loadUserByUsername("crew@co.com")).thenReturn(maintenanceCrew);
        when(userDetailsService.loadUserByUsername("platform@admin.com")).thenReturn(platformAdmin);

        companyAdminToken = "Bearer " + jwtUtil.generateToken(companyAdmin);
        fleetManagerToken = "Bearer " + jwtUtil.generateToken(fleetManager);
        fieldStaffToken = "Bearer " + jwtUtil.generateToken(fieldStaff);
        maintenanceCrewToken = "Bearer " + jwtUtil.generateToken(maintenanceCrew);
        platformAdminToken = "Bearer " + jwtUtil.generateToken(platformAdmin);
    }
}
