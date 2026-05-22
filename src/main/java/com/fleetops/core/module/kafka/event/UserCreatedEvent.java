package com.fleetops.core.module.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserCreatedEvent {
    private Long userId;
    private String userName;
    private String userEmail;
    private String role;
    private String temporaryPassword;
}
