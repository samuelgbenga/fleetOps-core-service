package com.fleetops.core.module.triprequest.service;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.model.CompanyStatus;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.outbox.service.OutboxService;
import com.fleetops.core.module.triprequest.dto.RejectTripRequest;
import com.fleetops.core.module.triprequest.dto.TripRequestCreate;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.triprequest.repository.VehicleAssignmentRepository;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.shared.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class TripRequestServiceIntegrationTest {

    @Autowired private TripRequestService tripRequestService;
    @Autowired private TripRequestRepository tripRequestRepository;
    @Autowired private VehicleAssignmentRepository assignmentRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Prevents real Kafka publishing — the producers delegate to OutboxService
    @MockBean private OutboxService outboxService;

    private Company company;
    private User requester;
    private User manager;
    private Vehicle vehicle;

    private static final String REQUESTER_EMAIL = "field.staff@it.fleetops.com";
    private static final String MANAGER_EMAIL   = "fleet.manager@it.fleetops.com";

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.builder()
                .name("Integration Test Fleet Co")
                .email("it@fleetops-test.com")
                .status(CompanyStatus.APPROVED)
                .build());

        String encodedPw = passwordEncoder.encode("TestPass@1");

        requester = userRepository.save(User.builder()
                .name("Field Staff")
                .email(REQUESTER_EMAIL)
                .password(encodedPw)
                .role(Role.FIELD_STAFF)
                .userType(UserType.COMPANY)
                .company(company)
                .active(true)
                .totalJobsCompleted(0)
                .build());

        manager = userRepository.save(User.builder()
                .name("Fleet Manager")
                .email(MANAGER_EMAIL)
                .password(encodedPw)
                .role(Role.FLEET_MANAGER)
                .userType(UserType.COMPANY)
                .company(company)
                .active(true)
                .totalJobsCompleted(0)
                .build());

        vehicle = vehicleRepository.save(Vehicle.builder()
                .company(company)
                .make("Toyota")
                .model("Camry")
                .plateNumber("IT-001-TEST")
                .status(VehicleStatus.AVAILABLE)
                .currentMileage(0.0)
                .milestoneInterval(10000.0)
                .build());

        asRequester();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_persistsTripToDatabase() {
        tripRequestService.create(tripDto("Lagos"));

        List<TripRequest> all = tripRequestRepository.findByCompanyId(company.getId());
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getDestination()).isEqualTo("Lagos");
    }

    @Test
    void create_tripHasPendingStatus() {
        tripRequestService.create(tripDto("Abuja"));

        var trips = tripRequestRepository.findByCompanyId(company.getId());
        assertThat(trips.get(0).getStatus()).isEqualTo(TripRequestStatus.PENDING);
    }

    @Test
    void create_vehicleRemainsAvailableAfterRequest() {
        tripRequestService.create(tripDto("Port Harcourt"));

        var v = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void create_destinationSavedCorrectly() {
        tripRequestService.create(tripDto("Kano"));

        var trips = tripRequestRepository.findByCompanyId(company.getId());
        assertThat(trips.get(0).getDestination()).isEqualTo("Kano");
    }

    @Test
    void create_requesterLinkedToTrip() {
        tripRequestService.create(tripDto("Enugu"));

        var trips = tripRequestRepository.findByCompanyId(company.getId());
        assertThat(trips.get(0).getRequestedBy().getId()).isEqualTo(requester.getId());
    }

    // ─── approve ──────────────────────────────────────────────────────────────

    @Test
    void approve_vehicleStatusBecomesAssigned() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.approve(created.getId());

        var v = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
    }

    @Test
    void approve_tripStatusBecomesApproved() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.approve(created.getId());

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.APPROVED);
    }

    @Test
    void approve_assignmentRecordIsCreated() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.approve(created.getId());

        var assignments = assignmentRepository.findByVehicleId(vehicle.getId());
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getAssignedTo().getId()).isEqualTo(requester.getId());
    }

    @Test
    void approve_setsApprovedAtTimestamp() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.approve(created.getId());

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getApprovedAt()).isNotNull();
        assertThat(trip.getApprovedAt()).isBefore(LocalDateTime.now().plusSeconds(5));
    }

    @Test
    void approve_rejectsOtherPendingTripsForSameVehicle() {
        var trip1 = tripRequestService.create(tripDto("Lagos"));
        var trip2 = tripRequestService.create(tripDto("Abuja"));
        asManager();

        tripRequestService.approve(trip1.getId());

        var other = tripRequestRepository.findById(trip2.getId()).orElseThrow();
        assertThat(other.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
        assertThat(other.getRejectionReason()).contains("approved for another trip");
    }

    // ─── complete ─────────────────────────────────────────────────────────────

    @Test
    void complete_tripStatusBecomesCompleted() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();
        tripRequestService.approve(created.getId());

        asRequester();
        tripRequestService.complete(created.getId());
        asManager();
        tripRequestService.confirmCompletion(created.getId());

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.COMPLETED);
    }

    @Test
    void complete_vehicleStatusBecomesAvailableAgain() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();
        tripRequestService.approve(created.getId());

        asRequester();
        tripRequestService.complete(created.getId());
        asManager();
        tripRequestService.confirmCompletion(created.getId());

        var v = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void complete_setsCompletedAtTimestamp() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();
        tripRequestService.approve(created.getId());

        asRequester();
        tripRequestService.complete(created.getId());
        asManager();
        tripRequestService.confirmCompletion(created.getId());

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getCompletedAt()).isNotNull();
    }

    // ─── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_tripStatusBecomesRejected() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.reject(created.getId(), rejectDto("No budget"));

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
    }

    @Test
    void reject_rejectionReasonPersistedToDatabase() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.reject(created.getId(), rejectDto("Policy violation"));

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getRejectionReason()).isEqualTo("Policy violation");
    }

    @Test
    void reject_vehicleRemainsAvailable() {
        var created = tripRequestService.create(tripDto("Lagos"));
        asManager();

        tripRequestService.reject(created.getId(), rejectDto("Reason"));

        var v = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    // ─── auto-reject stale ────────────────────────────────────────────────────

    @Test
    void autoRejectStale_stalePendingRequestsAreRejected() {
        var created = tripRequestService.create(tripDto("Lagos"));

        // Force created_at to 4 days ago via raw JDBC to bypass @PrePersist and updatable=false
        jdbcTemplate.update(
                "UPDATE trip_requests SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(4)),
                created.getId());

        tripRequestService.autoRejectStaleRequests();

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
        assertThat(trip.getRejectionReason()).contains("Auto-rejected");
    }

    @Test
    void autoRejectStale_recentRequestsAreUntouched() {
        tripRequestService.create(tripDto("Lagos"));

        tripRequestService.autoRejectStaleRequests();

        var trips = tripRequestRepository.findByCompanyId(company.getId());
        assertThat(trips).allMatch(t -> t.getStatus() == TripRequestStatus.PENDING);
    }

    @Test
    void autoRejectStale_multipleStaleRequestsAllRejected() {
        var trip1 = tripRequestService.create(tripDto("Lagos"));
        var trip2 = tripRequestService.create(tripDto("Kano"));

        Timestamp stale = Timestamp.valueOf(LocalDateTime.now().minusDays(5));
        jdbcTemplate.update("UPDATE trip_requests SET created_at = ? WHERE id = ?", stale, trip1.getId());
        jdbcTemplate.update("UPDATE trip_requests SET created_at = ? WHERE id = ?", stale, trip2.getId());

        tripRequestService.autoRejectStaleRequests();

        assertThat(tripRequestRepository.findById(trip1.getId()).orElseThrow().getStatus())
                .isEqualTo(TripRequestStatus.REJECTED);
        assertThat(tripRequestRepository.findById(trip2.getId()).orElseThrow().getStatus())
                .isEqualTo(TripRequestStatus.REJECTED);
    }

    // ─── multi-tenant isolation ───────────────────────────────────────────────

    @Test
    void getAll_onlyReturnsCurrentCompanyTrips() {
        tripRequestService.create(tripDto("Lagos"));

        // Create a completely separate company with its own trip
        Company other = companyRepository.save(Company.builder()
                .name("Other Fleet Co")
                .email("other@fleet-it.com")
                .status(CompanyStatus.APPROVED)
                .build());
        User otherUser = userRepository.save(User.builder()
                .name("Other User")
                .email("other@fleet-it.com")
                .password(passwordEncoder.encode("TestPass@1"))
                .role(Role.FIELD_STAFF)
                .userType(UserType.COMPANY)
                .company(other)
                .active(true)
                .totalJobsCompleted(0)
                .build());
        Vehicle otherVehicle = vehicleRepository.save(Vehicle.builder()
                .company(other)
                .make("Honda")
                .model("Accord")
                .plateNumber("OT-999-TEST")
                .status(VehicleStatus.AVAILABLE)
                .currentMileage(0.0)
                .milestoneInterval(10000.0)
                .build());

        TenantContext.set(other.getId(), otherUser.getId(), "FIELD_STAFF", "COMPANY");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otherUser.getEmail(), null, List.of()));

        var otherReq = new TripRequestCreate();
        otherReq.setVehicleId(otherVehicle.getId());
        otherReq.setDestination("Ibadan");
        otherReq.setStartDate(LocalDate.now().plusDays(1));
        otherReq.setEndDate(LocalDate.now().plusDays(3));
        tripRequestService.create(otherReq);

        // Switch back — should only see our company's trip
        asRequester();
        var trips = tripRequestService.getAll();
        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).getDestination()).isEqualTo("Lagos");
    }

    // ─── full lifecycle ────────────────────────────────────────────────────────

    @Test
    void fullLifecycle_createApproveComplete_vehicleEndsAvailable() {
        // 1. Request a trip
        var created = tripRequestService.create(tripDto("Benin City"));
        assertThat(created.getStatus()).isEqualTo(TripRequestStatus.PENDING.name());

        var vAfterCreate = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(vAfterCreate.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        // 2. Manager approves
        asManager();
        var approved = tripRequestService.approve(created.getId());
        assertThat(approved.getStatus()).isEqualTo(TripRequestStatus.APPROVED.name());

        var vAfterApprove = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(vAfterApprove.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
        assertThat(assignmentRepository.findByVehicleId(vehicle.getId())).hasSize(1);

        // 3. Field staff marks trip as completed (awaiting manager confirmation)
        asRequester();
        var pendingCompletion = tripRequestService.complete(created.getId());
        assertThat(pendingCompletion.getStatus()).isEqualTo(TripRequestStatus.PENDING_COMPLETION.name());

        var vAfterComplete = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(vAfterComplete.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);

        // 4. Manager confirms completion
        asManager();
        tripRequestService.confirmCompletion(created.getId());

        var vAfterConfirm = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(vAfterConfirm.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        var finalTrip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(finalTrip.getStatus()).isEqualTo(TripRequestStatus.COMPLETED);
        assertThat(finalTrip.getApprovedAt()).isNotNull();
        assertThat(finalTrip.getCompletedAt()).isNotNull();
    }

    @Test
    void fullLifecycle_createAndReject_vehicleStaysAvailable() {
        var created = tripRequestService.create(tripDto("Owerri"));
        asManager();

        tripRequestService.reject(created.getId(), rejectDto("Budget cut"));

        var v = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        var trip = tripRequestRepository.findById(created.getId()).orElseThrow();
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
        assertThat(assignmentRepository.findByVehicleId(vehicle.getId())).isEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private TripRequestCreate tripDto(String destination) {
        var req = new TripRequestCreate();
        req.setVehicleId(vehicle.getId());
        req.setDestination(destination);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(3));
        return req;
    }

    private RejectTripRequest rejectDto(String reason) {
        var req = new RejectTripRequest();
        req.setReason(reason);
        return req;
    }

    private void asRequester() {
        TenantContext.set(company.getId(), requester.getId(), "FIELD_STAFF", "COMPANY");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(REQUESTER_EMAIL, null, List.of()));
    }

    private void asManager() {
        TenantContext.set(company.getId(), manager.getId(), "FLEET_MANAGER", "COMPANY");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MANAGER_EMAIL, null, List.of()));
    }
}
