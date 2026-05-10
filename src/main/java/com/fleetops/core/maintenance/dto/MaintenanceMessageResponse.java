package com.fleetops.core.maintenance.dto;

import com.fleetops.core.maintenance.entity.MaintenanceMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MaintenanceMessageResponse {

    private Long id;
    private Long flagId;
    private Long senderId;
    private String senderName;
    private String senderRole;
    private String message;
    private LocalDateTime sentAt;

    public static MaintenanceMessageResponse from(MaintenanceMessage msg) {
        return MaintenanceMessageResponse.builder()
                .id(msg.getId())
                .flagId(msg.getFlag().getId())
                .senderId(msg.getSender().getId())
                .senderName(msg.getSender().getName())
                .senderRole(msg.getSender().getRole().name())
                .message(msg.getMessage())
                .sentAt(msg.getSentAt())
                .build();
    }
}
