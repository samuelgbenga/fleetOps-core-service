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
import com.fleetops.core.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceMessageService {

    private final MaintenanceMessageRepository messageRepository;
    private final MaintenanceFlagRepository flagRepository;
    private final UserRepository userRepository;

    @Transactional
    public MaintenanceMessageResponse sendMessage(Long flagId, MaintenanceMessageRequest request) {
        MaintenanceFlag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance flag not found: " + flagId));

        if (flag.getStatus() == FlagStatus.RESOLVED) {
            throw new ConflictException(
                    "This maintenance conversation is locked. Flag " + flagId + " has been resolved.");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MaintenanceMessage message = MaintenanceMessage.builder()
                .flag(flag)
                .sender(sender)
                .message(request.getMessage())
                .build();

        return MaintenanceMessageResponse.from(messageRepository.save(message));
    }

    public List<MaintenanceMessageResponse> getMessages(Long flagId) {
        flagRepository.findById(flagId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance flag not found: " + flagId));

        return messageRepository.findByFlagIdOrderBySentAtAsc(flagId)
                .stream()
                .map(MaintenanceMessageResponse::from)
                .toList();
    }
}
