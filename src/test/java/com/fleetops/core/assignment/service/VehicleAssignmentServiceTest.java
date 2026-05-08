package com.fleetops.core.assignment.service;

import com.fleetops.core.assignment.dto.AssignmentResponse;
import com.fleetops.core.assignment.entity.VehicleAssignment;
import com.fleetops.core.assignment.repository.VehicleAssignmentRepository;
import com.fleetops.core.triprequest.entity.TripRequest;
import com.fleetops.core.triprequest.enums.TripRequestStatus;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleAssignmentServiceTest {

    @Mock private VehicleAssignmentRepository vehicleAssignmentRepository;
    @InjectMocks private VehicleAssignmentService vehicleAssignmentService;

    @Test
    void getAssignmentsByVehicle_returnsMappedDTOs() {
        User staff = User.builder().id(1L).name("John").email("john@fleetops.com")
                .role(UserRole.FIELD_STAFF).password("hashed").build();
        Vehicle vehicle = Vehicle.builder().id(10L).make("Toyota").model("Camry")
                .plateNumber("ABC-123").status(VehicleStatus.ASSIGNED)
                .currentMileage(0.0).milestoneInterval(5000.0).build();
        TripRequest trip = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .destination("Lagos").status(TripRequestStatus.APPROVED)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();
        VehicleAssignment assignment = VehicleAssignment.builder()
                .id(1L).vehicle(vehicle).tripRequest(trip)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3))
                .assignedAt(LocalDateTime.now()).build();

        when(vehicleAssignmentRepository.findByVehicleId(10L)).thenReturn(List.of(assignment));

        List<AssignmentResponse> result = vehicleAssignmentService.getAssignmentsByVehicle(10L);

        assertThat(result).hasSize(1);
        AssignmentResponse dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getVehicleId()).isEqualTo(10L);
        assertThat(dto.getPlateNumber()).isEqualTo("ABC-123");
        assertThat(dto.getTripRequestId()).isEqualTo(100L);
        assertThat(dto.getFieldStaffId()).isEqualTo(1L);
        assertThat(dto.getFieldStaffName()).isEqualTo("John");
        assertThat(dto.getDestination()).isEqualTo("Lagos");
    }

    @Test
    void getAssignmentsByVehicle_noAssignments_returnsEmptyList() {
        when(vehicleAssignmentRepository.findByVehicleId(10L)).thenReturn(List.of());

        assertThat(vehicleAssignmentService.getAssignmentsByVehicle(10L)).isEmpty();
    }

    @Test
    void getAssignmentsByVehicle_multipleAssignments_returnsAll() {
        User staff = User.builder().id(1L).name("John").email("john@fleetops.com")
                .role(UserRole.FIELD_STAFF).password("hashed").build();
        Vehicle vehicle = Vehicle.builder().id(10L).make("Toyota").model("Camry")
                .plateNumber("ABC-123").status(VehicleStatus.AVAILABLE)
                .currentMileage(0.0).milestoneInterval(5000.0).build();

        TripRequest trip1 = TripRequest.builder().id(100L).fieldStaff(staff).vehicle(vehicle)
                .destination("Lagos").status(TripRequestStatus.COMPLETED)
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().minusDays(3)).build();
        TripRequest trip2 = TripRequest.builder().id(101L).fieldStaff(staff).vehicle(vehicle)
                .destination("Abuja").status(TripRequestStatus.APPROVED)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3)).build();

        VehicleAssignment a1 = VehicleAssignment.builder().id(1L).vehicle(vehicle).tripRequest(trip1)
                .startDate(trip1.getStartDate()).endDate(trip1.getEndDate())
                .assignedAt(LocalDateTime.now().minusDays(6)).build();
        VehicleAssignment a2 = VehicleAssignment.builder().id(2L).vehicle(vehicle).tripRequest(trip2)
                .startDate(trip2.getStartDate()).endDate(trip2.getEndDate())
                .assignedAt(LocalDateTime.now()).build();

        when(vehicleAssignmentRepository.findByVehicleId(10L)).thenReturn(List.of(a1, a2));

        List<AssignmentResponse> result = vehicleAssignmentService.getAssignmentsByVehicle(10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AssignmentResponse::getDestination)
                .containsExactlyInAnyOrder("Lagos", "Abuja");
    }
}
