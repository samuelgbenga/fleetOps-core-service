package com.fleetops.core.module.maintenance.dto;

import com.fleetops.core.module.maintenance.model.MaintenanceMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponse {
    private Long id;
    private Long flagId;
    private Long senderId;
    private String senderName;
    private String senderRole;
    private String message;
    private LocalDateTime sentAt;

    public static MessageResponse from(MaintenanceMessage m) {
        return MessageResponse.builder()
                .id(m.getId())
                .flagId(m.getFlag().getId())
                .senderId(m.getSender().getId())
                .senderName(m.getSenderName())
                .senderRole(m.getSenderRole())
                .message(m.getMessage())
                .sentAt(m.getSentAt())
                .build();
    }
}
