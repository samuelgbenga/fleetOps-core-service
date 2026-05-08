package com.fleetops.core.vehicle.entity;

import com.fleetops.core.vehicle.enums.VehicleStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleEntityTest {

    // ── isAvailable ─────────────────────────────────────────────────────────

    @Test
    void isAvailable_whenStatusIsAvailable_returnsTrue() {
        Vehicle vehicle = Vehicle.builder().status(VehicleStatus.AVAILABLE).build();
        assertThat(vehicle.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_whenStatusIsAssigned_returnsFalse() {
        Vehicle vehicle = Vehicle.builder().status(VehicleStatus.ASSIGNED).build();
        assertThat(vehicle.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_whenStatusIsMaintenance_returnsFalse() {
        Vehicle vehicle = Vehicle.builder().status(VehicleStatus.MAINTENANCE).build();
        assertThat(vehicle.isAvailable()).isFalse();
    }

    // ── isMilestoneReached ───────────────────────────────────────────────────

    @Test
    void isMilestoneReached_justCrossesBoundary_returnsTrue() {
        // old=4999, new=5001  → floor(4999/5000)=0, floor(5001/5000)=1 → true
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(5001.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(4999.0)).isTrue();
    }

    @Test
    void isMilestoneReached_exactlyAtBoundary_returnsTrue() {
        // old=4999, new=5000 → floor(0.9998)=0, floor(1.0)=1 → true
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(5000.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(4999.0)).isTrue();
    }

    @Test
    void isMilestoneReached_withinSameInterval_returnsFalse() {
        // old=4998, new=4999 → both floor to 0 → false
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(4999.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(4998.0)).isFalse();
    }

    @Test
    void isMilestoneReached_bothAboveSameMilestone_returnsFalse() {
        // old=5001, new=5500 → both floor to 1 → false
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(5500.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(5001.0)).isFalse();
    }

    @Test
    void isMilestoneReached_largeJumpAcrossMultipleMilestones_returnsTrue() {
        // old=4999, new=10001 → floor(0)=0, floor(2)=2 → true
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(10001.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(4999.0)).isTrue();
    }

    @Test
    void isMilestoneReached_secondMilestoneExact_returnsTrue() {
        // old=9999, new=10000 → floor(1)=1, floor(2)=2 → true
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(10000.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(9999.0)).isTrue();
    }

    @Test
    void isMilestoneReached_customInterval_crossesBoundary_returnsTrue() {
        // interval=3000, old=2999, new=3001
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(3001.0)
                .milestoneInterval(3000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(2999.0)).isTrue();
    }

    @Test
    void isMilestoneReached_fromZero_smallAdd_returnsFalse() {
        // old=0, new=100, interval=5000 → both floor to 0 → false
        Vehicle vehicle = Vehicle.builder()
                .currentMileage(100.0)
                .milestoneInterval(5000.0)
                .build();
        assertThat(vehicle.isMilestoneReached(0.0)).isFalse();
    }
}
