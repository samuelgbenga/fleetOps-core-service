package com.fleetops.core.module.triprequest.service;

import com.fleetops.core.module.company.model.Company;
import com.fleetops.core.module.company.repository.CompanyRepository;
import com.fleetops.core.module.kafka.event.NotificationRequestEvent;
import com.fleetops.core.module.kafka.event.VehicleActivityEvent;
import com.fleetops.core.module.kafka.producer.NotificationEventProducer;
import com.fleetops.core.module.kafka.producer.VehicleActivityProducer;
import com.fleetops.core.module.triprequest.dto.RejectTripRequest;
import com.fleetops.core.module.triprequest.dto.TripRequestCreate;
import com.fleetops.core.module.triprequest.model.TripRequest;
import com.fleetops.core.module.triprequest.model.TripRequestStatus;
import com.fleetops.core.module.triprequest.model.VehicleAssignment;
import com.fleetops.core.module.triprequest.repository.TripRequestRepository;
import com.fleetops.core.module.triprequest.repository.VehicleAssignmentRepository;
import com.fleetops.core.module.triprequest.service.impl.TripRequestServiceImpl;
import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import com.fleetops.core.module.vehicle.model.Vehicle;
import com.fleetops.core.module.vehicle.model.VehicleStatus;
import com.fleetops.core.module.vehicle.repository.VehicleRepository;
import com.fleetops.core.shared.context.TenantContext;
import com.fleetops.core.shared.exception.ConflictException;
import com.fleetops.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripRequestServiceImplTest {

    @Mock TripRequestRepository tripRequestRepository;
    @Mock VehicleAssignmentRepository assignmentRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock NotificationEventProducer notificationEventProducer;
    @Mock VehicleActivityProducer vehicleActivityProducer;

    @InjectMocks TripRequestServiceImpl tripRequestService;

    private static final Long COMPANY_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@test.com";

    @BeforeEach
    void setUp() {
        TenantContext.set(COMPANY_ID, USER_ID, "FLEET_MANAGER", "COMPANY");
        var auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Company buildCompany() {
        return Company.builder().id(COMPANY_ID).name("Fleet Co").build();
    }

    private Vehicle buildAvailableVehicle(Long id) {
        return Vehicle.builder().id(id).company(buildCompany()).plateNumber("ABC-123-XY")
                .status(VehicleStatus.AVAILABLE).make("Toyota").model("Camry")
                .currentMileage(0.0).milestoneInterval(10000.0).build();
    }

    private User buildUser(Long id, String email, Role role) {
        return User.builder().id(id).name("Test User").email(email)
                .password("enc").role(role).userType(UserType.COMPANY)
                .company(buildCompany()).active(true).build();
    }

    private TripRequest buildTrip(Long id, TripRequestStatus status, Vehicle vehicle, User requester) {
        return TripRequest.builder().id(id).company(buildCompany()).vehicle(vehicle)
                .requestedBy(requester).destination("Lagos").status(status)
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(2)).build();
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_returnsResponse() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        assertThat(tripRequestService.create(req)).isNotNull();
    }

    @Test
    void create_vehicleNotAvailableThrows() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        vehicle.setStatus(VehicleStatus.ASSIGNED);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));
        assertThatThrownBy(() -> tripRequestService.create(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_vehicleNotAvailableNeverSaves() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        vehicle.setStatus(VehicleStatus.MAINTENANCE);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));

        var req = new TripRequestCreate();
        req.setVehicleId(1L);
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));
        assertThatThrownBy(() -> tripRequestService.create(req));
        verify(tripRequestRepository, never()).save(any());
    }

    @Test
    void create_companyNotFoundThrows() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());
        var req = new TripRequestCreate(); req.setVehicleId(1L);
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));
        assertThatThrownBy(() -> tripRequestService.create(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_vehicleNotFoundThrows() {
        Company company = buildCompany();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.empty());
        var req = new TripRequestCreate(); req.setVehicleId(1L);
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));
        assertThatThrownBy(() -> tripRequestService.create(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_publishesActivityEvent() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        tripRequestService.create(req);
        verify(vehicleActivityProducer).publish(any(VehicleActivityEvent.class));
    }

    @Test
    void create_saveCalledOnce() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        tripRequestService.create(req);
        verify(tripRequestRepository, times(1)).save(any());
    }

    @Test
    void create_userNotFoundThrows() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        var req = new TripRequestCreate(); req.setVehicleId(1L);
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));
        assertThatThrownBy(() -> tripRequestService.create(req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_destinationSetOnTrip() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Abuja");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        tripRequestService.create(req);
        ArgumentCaptor<TripRequest> captor = ArgumentCaptor.forClass(TripRequest.class);
        verify(tripRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getDestination()).isEqualTo("Abuja");
    }

    @Test
    void create_statusIsPending() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        tripRequestService.create(req);
        ArgumentCaptor<TripRequest> captor = ArgumentCaptor.forClass(TripRequest.class);
        verify(tripRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TripRequestStatus.PENDING);
    }

    @Test
    void create_publishesRequesterNotification() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        tripRequestService.create(req);
        verify(notificationEventProducer, atLeastOnce()).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void create_usesCompanyIdFromTenantContext() {
        Company company = buildCompany();
        Vehicle vehicle = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, vehicle, requester);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(vehicleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(vehicle));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(requester));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        var req = new TripRequestCreate();
        req.setVehicleId(1L); req.setDestination("Lagos");
        req.setStartDate(LocalDate.now().plusDays(1)); req.setEndDate(LocalDate.now().plusDays(2));

        tripRequestService.create(req);
        verify(companyRepository).findById(COMPANY_ID);
        verify(vehicleRepository).findByIdAndCompanyId(1L, COMPANY_ID);
    }

    // ─── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsCompanyTrips() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildTrip(1L, TripRequestStatus.PENDING, v, u)));
        assertThat(tripRequestService.getAll()).hasSize(1);
    }

    @Test
    void getAll_emptyList() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        assertThat(tripRequestService.getAll()).isEmpty();
    }

    @Test
    void getAll_usesCompanyIdFromTenant() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verify(tripRequestRepository).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getAll_neverSaves() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verify(tripRequestRepository, never()).save(any());
    }

    @Test
    void getAll_noNotificationInteraction() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getAll_multipleTrips() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildTrip(1L, TripRequestStatus.PENDING, v, u),
                        buildTrip(2L, TripRequestStatus.APPROVED, v, u)));
        assertThat(tripRequestService.getAll()).hasSize(2);
    }

    @Test
    void getAll_mapsDestinations() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(
                List.of(buildTrip(1L, TripRequestStatus.PENDING, v, u)));
        assertThat(tripRequestService.getAll().get(0).getDestination()).isEqualTo("Lagos");
    }

    @Test
    void getAll_findByCompanyIdCalledOnce() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verify(tripRequestRepository, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test
    void getAll_noActivityProducerInteraction() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void getAll_noVehicleRepoInteraction() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getAll_noUserRepoInteraction() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verifyNoInteractions(userRepository);
    }

    @Test
    void getAll_noAssignmentRepoInteraction() {
        when(tripRequestRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
        tripRequestService.getAll();
        verifyNoInteractions(assignmentRepository);
    }

    // ─── getMy ────────────────────────────────────────────────────────────────

    @Test
    void getMy_returnsMyTrips() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(buildTrip(1L, TripRequestStatus.PENDING, v, u)));
        assertThat(tripRequestService.getMy()).hasSize(1);
    }

    @Test
    void getMy_usesUserIdAndCompanyId() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verify(tripRequestRepository).findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID);
    }

    @Test
    void getMy_emptyList() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        assertThat(tripRequestService.getMy()).isEmpty();
    }

    @Test
    void getMy_neverSaves() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verify(tripRequestRepository, never()).save(any());
    }

    @Test
    void getMy_noNotificationInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getMy_noActivityProducerInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void getMy_noUserRepoInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verifyNoInteractions(userRepository);
    }

    @Test
    void getMy_multipleTrips() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(buildTrip(1L, TripRequestStatus.PENDING, v, u),
                        buildTrip(2L, TripRequestStatus.REJECTED, v, u)));
        assertThat(tripRequestService.getMy()).hasSize(2);
    }

    @Test
    void getMy_calledOnce() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verify(tripRequestRepository, times(1)).findByRequestedByIdAndCompanyId(any(), any());
    }

    @Test
    void getMy_noVehicleRepoInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void getMy_noCompanyRepoInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMy();
        verifyNoInteractions(companyRepository);
    }

    // ─── getMyApproved ────────────────────────────────────────────────────────

    @Test
    void getMyApproved_returnsOnlyApproved() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest approved = buildTrip(1L, TripRequestStatus.APPROVED, v, u);
        TripRequest pending = buildTrip(2L, TripRequestStatus.PENDING, v, u);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(approved, pending));
        assertThat(tripRequestService.getMyApproved()).hasSize(1);
    }

    @Test
    void getMyApproved_approvedTripInResult() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest approved = buildTrip(1L, TripRequestStatus.APPROVED, v, u);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(approved));
        assertThat(tripRequestService.getMyApproved().get(0).getStatus())
                .isEqualTo(TripRequestStatus.APPROVED.name());
    }

    @Test
    void getMyApproved_emptyWhenNoneApproved() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(buildTrip(1L, TripRequestStatus.PENDING, v, u)));
        assertThat(tripRequestService.getMyApproved()).isEmpty();
    }

    @Test
    void getMyApproved_emptyList() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        assertThat(tripRequestService.getMyApproved()).isEmpty();
    }

    @Test
    void getMyApproved_neverSaves() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMyApproved();
        verify(tripRequestRepository, never()).save(any());
    }

    @Test
    void getMyApproved_noActivityProducerInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMyApproved();
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void getMyApproved_noNotificationInteraction() {
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());
        tripRequestService.getMyApproved();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void getMyApproved_filtersRejected() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(buildTrip(1L, TripRequestStatus.REJECTED, v, u)));
        assertThat(tripRequestService.getMyApproved()).isEmpty();
    }

    @Test
    void getMyApproved_filtersCompleted() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(buildTrip(1L, TripRequestStatus.COMPLETED, v, u)));
        assertThat(tripRequestService.getMyApproved()).isEmpty();
    }

    @Test
    void getMyApproved_multipleApproved() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        when(tripRequestRepository.findByRequestedByIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(buildTrip(1L, TripRequestStatus.APPROVED, v, u),
                        buildTrip(2L, TripRequestStatus.APPROVED, v, u)));
        assertThat(tripRequestService.getMyApproved()).hasSize(2);
    }

    // ─── approve ──────────────────────────────────────────────────────────────

    @Test
    void approve_setsStatusApproved() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.APPROVED);
    }

    @Test
    void approve_vehicleSetToAssigned() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
    }

    @Test
    void approve_notPendingThrows() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        assertThatThrownBy(() -> tripRequestService.approve(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void approve_vehicleNotAvailableThrows() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.MAINTENANCE);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> tripRequestService.approve(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void approve_notFoundThrows() {
        when(tripRequestRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tripRequestService.approve(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_createsAssignment() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        verify(assignmentRepository).save(any(VehicleAssignment.class));
    }

    @Test
    void approve_sendsNotification() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void approve_setsApprovedAt() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        assertThat(trip.getApprovedAt()).isNotNull();
    }

    @Test
    void approve_otherPendingTripRejected() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);
        TripRequest other = buildTrip(2L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING))
                .thenReturn(List.of(other));
        when(tripRequestRepository.save(any())).thenReturn(trip);

        tripRequestService.approve(1L);
        assertThat(other.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
    }

    @Test
    void approve_publishesActivityEvent() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        verify(vehicleActivityProducer, times(1)).publish(any(VehicleActivityEvent.class));
    }

    @Test
    void approve_returnsResponse() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        assertThat(tripRequestService.approve(1L)).isNotNull();
    }

    @Test
    void approve_notificationTypeCorrect() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        User approver = buildUser(2L, EMAIL, Role.FLEET_MANAGER);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdWithLock(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(v)).thenReturn(v);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(approver));
        when(assignmentRepository.save(any())).thenReturn(mock(VehicleAssignment.class));
        when(tripRequestRepository.findByVehicleIdAndStatus(1L, TripRequestStatus.PENDING)).thenReturn(List.of());
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.approve(1L);
        ArgumentCaptor<NotificationRequestEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("TRIP_APPROVED");
    }

    // ─── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_setsStatusRejected() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Not authorized");
        tripRequestService.reject(1L, req);
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
    }

    @Test
    void reject_setsRejectionReason() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Not authorized");
        tripRequestService.reject(1L, req);
        assertThat(trip.getRejectionReason()).isEqualTo("Not authorized");
    }

    @Test
    void reject_notPendingThrows() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        assertThatThrownBy(() -> tripRequestService.reject(1L, new RejectTripRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reject_notFoundThrows() {
        when(tripRequestRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tripRequestService.reject(99L, new RejectTripRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reject_sendsNotification() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Reason");
        tripRequestService.reject(1L, req);
        verify(notificationEventProducer).publish(any(NotificationRequestEvent.class));
    }

    @Test
    void reject_savesCalled() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Reason");
        tripRequestService.reject(1L, req);
        verify(tripRequestRepository).save(trip);
    }

    @Test
    void reject_notificationTypeCorrect() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Reason");
        tripRequestService.reject(1L, req);

        ArgumentCaptor<NotificationRequestEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("TRIP_REJECTED");
    }

    @Test
    void reject_returnsResponse() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Reason");
        assertThat(tripRequestService.reject(1L, req)).isNotNull();
    }

    @Test
    void reject_noActivityProducerInteraction() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Reason");
        tripRequestService.reject(1L, req);
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void reject_vehicleStatusUnchanged() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        var req = new RejectTripRequest();
        req.setReason("Reason");
        tripRequestService.reject(1L, req);
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void reject_longReasonStored() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        String longReason = "A".repeat(500);
        var req = new RejectTripRequest();
        req.setReason(longReason);
        tripRequestService.reject(1L, req);
        assertThat(trip.getRejectionReason()).isEqualTo(longReason);
    }

    // ─── complete ─────────────────────────────────────────────────────────────

    @Test
    void complete_setsStatusPendingCompletion() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.complete(1L);
        assertThat(trip.getStatus()).isEqualTo(TripRequestStatus.PENDING_COMPLETION);
    }

    @Test
    void complete_vehicleRemainsAssigned() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.complete(1L);
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
    }

    @Test
    void complete_notApprovedThrows() {
        Vehicle v = buildAvailableVehicle(1L);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.PENDING, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        assertThatThrownBy(() -> tripRequestService.complete(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void complete_notFoundThrows() {
        when(tripRequestRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tripRequestService.complete(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void complete_doesNotSetCompletedAt() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.complete(1L);
        assertThat(trip.getCompletedAt()).isNull();
    }

    @Test
    void complete_publishesNoActivityEvent() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.complete(1L);
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void complete_noActivityProducerInteraction() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.complete(1L);
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void complete_returnsResponse() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        assertThat(tripRequestService.complete(1L)).isNotNull();
    }

    @Test
    void complete_savesTripOnly() {
        Vehicle v = buildAvailableVehicle(1L);
        v.setStatus(VehicleStatus.ASSIGNED);
        User requester = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest trip = buildTrip(1L, TripRequestStatus.APPROVED, v, requester);

        when(tripRequestRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(trip));
        when(tripRequestRepository.save(trip)).thenReturn(trip);

        tripRequestService.complete(1L);
        verify(vehicleRepository, never()).save(any());
        verify(tripRequestRepository).save(trip);
    }

    // ─── autoRejectStaleRequests ──────────────────────────────────────────────

    @Test
    void autoRejectStaleRequests_rejectsStaleTrips() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest stale = buildTrip(1L, TripRequestStatus.PENDING, v, u);

        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of(stale));
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of(stale));

        tripRequestService.autoRejectStaleRequests();
        assertThat(stale.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
    }

    @Test
    void autoRejectStaleRequests_setsAutoRejectionReason() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest stale = buildTrip(1L, TripRequestStatus.PENDING, v, u);

        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of(stale));
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of(stale));

        tripRequestService.autoRejectStaleRequests();
        assertThat(stale.getRejectionReason()).contains("Auto-rejected");
    }

    @Test
    void autoRejectStaleRequests_emptyListNoSave() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verify(tripRequestRepository).saveAll(argThat(list -> !((List<?>) list).iterator().hasNext()));
    }

    @Test
    void autoRejectStaleRequests_callsSaveAll() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verify(tripRequestRepository).saveAll(any());
    }

    @Test
    void autoRejectStaleRequests_multipleStaleTrips() {
        Vehicle v = buildAvailableVehicle(1L);
        User u = buildUser(USER_ID, EMAIL, Role.FIELD_STAFF);
        TripRequest s1 = buildTrip(1L, TripRequestStatus.PENDING, v, u);
        TripRequest s2 = buildTrip(2L, TripRequestStatus.PENDING, v, u);

        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of(s1, s2));
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of(s1, s2));

        tripRequestService.autoRejectStaleRequests();
        assertThat(s1.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
        assertThat(s2.getStatus()).isEqualTo(TripRequestStatus.REJECTED);
    }

    @Test
    void autoRejectStaleRequests_noNotificationInteraction() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void autoRejectStaleRequests_noActivityProducerInteraction() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verifyNoInteractions(vehicleActivityProducer);
    }

    @Test
    void autoRejectStaleRequests_callsFindStalePendingRequests() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verify(tripRequestRepository).findStalePendingRequests(any(LocalDateTime.class));
    }

    @Test
    void autoRejectStaleRequests_noVehicleRepoInteraction() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void autoRejectStaleRequests_noUserRepoInteraction() {
        when(tripRequestRepository.findStalePendingRequests(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(tripRequestRepository.saveAll(any())).thenReturn(List.of());

        tripRequestService.autoRejectStaleRequests();
        verifyNoInteractions(userRepository);
    }
}
