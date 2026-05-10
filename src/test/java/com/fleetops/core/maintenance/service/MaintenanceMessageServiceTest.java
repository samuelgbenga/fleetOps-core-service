package com.fleetops.core.maintenance.service;

import com.fleetops.core.exception.ConflictException;
import com.fleetops.core.exception.ResourceNotFoundException;
import com.fleetops.core.maintenance.dto.MaintenanceMessageRequest;
import com.fleetops.core.maintenance.dto.MaintenanceMessageResponse;
import com.fleetops.core.maintenance.entity.MaintenanceFlag;
import com.fleetops.core.maintenance.entity.MaintenanceMessage;
import com.fleetops.core.maintenance.enums.FlagStatus;
import com.fleetops.core.maintenance.repository.MaintenanceFlagRepository;
import com.fleetops.core.maintenance.repository.MaintenanceMessageRepository;
import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import com.fleetops.core.vehicle.entity.Vehicle;
import com.fleetops.core.vehicle.enums.VehicleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceMessageServiceTest {

    @Mock private MaintenanceMessageRepository messageRepository;
    @Mock private MaintenanceFlagRepository flagRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private MaintenanceMessageService messageService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── sendMessage ──────────────────────────────────────────────────────────

    @Test
    void sendMessage_success_savesAndReturnsResponse() {
        mockSecurityContext("tech@fleetops.com");
        User tech = maintenanceUser(1L, "tech@fleetops.com");
        MaintenanceFlag flag = openFlag(10L);

        when(flagRepository.findById(10L)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail("tech@fleetops.com")).thenReturn(Optional.of(tech));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            MaintenanceMessage m = inv.getArgument(0);
            m = MaintenanceMessage.builder()
                    .id(1L).flag(m.getFlag()).sender(m.getSender())
                    .message(m.getMessage()).sentAt(LocalDateTime.now()).build();
            return m;
        });

        MaintenanceMessageResponse response = messageService.sendMessage(10L, request("Engine checked"));

        assertThat(response.getMessage()).isEqualTo("Engine checked");
        assertThat(response.getSenderName()).isEqualTo("Tech User");
        assertThat(response.getSenderRole()).isEqualTo("MAINTENANCE_TEAM");
        assertThat(response.getFlagId()).isEqualTo(10L);
        verify(messageRepository).save(any(MaintenanceMessage.class));
    }

    @Test
    void sendMessage_resolvedFlag_throwsConflict() {
        MaintenanceFlag resolved = flagWithStatus(10L, FlagStatus.RESOLVED);
        when(flagRepository.findById(10L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> messageService.sendMessage(10L, request("still working")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("locked")
                .hasMessageContaining("resolved");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_flagNotFound_throwsResourceNotFound() {
        when(flagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(99L, request("hello")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_fleetManagerCanSend_success() {
        mockSecurityContext("manager@fleetops.com");
        User manager = fleetManager(2L, "manager@fleetops.com");
        MaintenanceFlag flag = flagWithStatus(10L, FlagStatus.IN_PROGRESS);

        when(flagRepository.findById(10L)).thenReturn(Optional.of(flag));
        when(userRepository.findByEmail("manager@fleetops.com")).thenReturn(Optional.of(manager));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            MaintenanceMessage m = inv.getArgument(0);
            return MaintenanceMessage.builder()
                    .id(2L).flag(m.getFlag()).sender(m.getSender())
                    .message(m.getMessage()).sentAt(LocalDateTime.now()).build();
        });

        MaintenanceMessageResponse response = messageService.sendMessage(10L, request("Please prioritise the brakes"));

        assertThat(response.getSenderRole()).isEqualTo("FLEET_MANAGER");
        assertThat(response.getMessage()).isEqualTo("Please prioritise the brakes");
    }

    // ── getMessages ──────────────────────────────────────────────────────────

    @Test
    void getMessages_success_returnsMessagesOrderedBySentAt() {
        MaintenanceFlag flag = openFlag(10L);
        User tech = maintenanceUser(1L, "tech@fleetops.com");
        User manager = fleetManager(2L, "manager@fleetops.com");

        MaintenanceMessage msg1 = MaintenanceMessage.builder()
                .id(1L).flag(flag).sender(tech)
                .message("Started inspection").sentAt(LocalDateTime.now().minusMinutes(10)).build();
        MaintenanceMessage msg2 = MaintenanceMessage.builder()
                .id(2L).flag(flag).sender(manager)
                .message("Good, keep going").sentAt(LocalDateTime.now().minusMinutes(5)).build();

        when(flagRepository.findById(10L)).thenReturn(Optional.of(flag));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(10L)).thenReturn(List.of(msg1, msg2));

        List<MaintenanceMessageResponse> result = messageService.getMessages(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessage()).isEqualTo("Started inspection");
        assertThat(result.get(1).getMessage()).isEqualTo("Good, keep going");
    }

    @Test
    void getMessages_resolvedFlag_stillReturnsMessages() {
        MaintenanceFlag resolved = flagWithStatus(10L, FlagStatus.RESOLVED);
        User tech = maintenanceUser(1L, "tech@fleetops.com");
        MaintenanceMessage msg = MaintenanceMessage.builder()
                .id(1L).flag(resolved).sender(tech)
                .message("Work done").sentAt(LocalDateTime.now().minusDays(1)).build();

        when(flagRepository.findById(10L)).thenReturn(Optional.of(resolved));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(10L)).thenReturn(List.of(msg));

        List<MaintenanceMessageResponse> result = messageService.getMessages(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("Work done");
    }

    @Test
    void getMessages_noMessages_returnsEmptyList() {
        when(flagRepository.findById(10L)).thenReturn(Optional.of(openFlag(10L)));
        when(messageRepository.findByFlagIdOrderBySentAtAsc(10L)).thenReturn(List.of());

        assertThat(messageService.getMessages(10L)).isEmpty();
    }

    @Test
    void getMessages_flagNotFound_throwsResourceNotFound() {
        when(flagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessages(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(auth.getName()).thenReturn(email);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private User maintenanceUser(Long id, String email) {
        return User.builder().id(id).name("Tech User").email(email)
                .role(UserRole.MAINTENANCE_TEAM).password("hashed").build();
    }

    private User fleetManager(Long id, String email) {
        return User.builder().id(id).name("Fleet Manager").email(email)
                .role(UserRole.FLEET_MANAGER).password("hashed").build();
    }

    private Vehicle vehicle(Long id) {
        return Vehicle.builder().id(id).make("Toyota").model("Hilux")
                .plateNumber("KJA-" + id + "AB").status(VehicleStatus.MAINTENANCE)
                .currentMileage(5000.0).milestoneInterval(5000.0).build();
    }

    private MaintenanceFlag openFlag(Long id) {
        return MaintenanceFlag.builder().id(id).vehicle(vehicle(id))
                .status(FlagStatus.OPEN).mileageAtTrigger(5000.0).build();
    }

    private MaintenanceFlag flagWithStatus(Long id, FlagStatus status) {
        return MaintenanceFlag.builder().id(id).vehicle(vehicle(id))
                .status(status).mileageAtTrigger(5000.0).build();
    }

    private MaintenanceMessageRequest request(String text) {
        MaintenanceMessageRequest req = new MaintenanceMessageRequest();
        req.setMessage(text);
        return req;
    }
}
