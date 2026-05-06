package com.fleetops.core.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestEvent {

    /** Email address of the person to notify */
    private String recipientEmail;

    /** Display name for the email greeting */
    private String recipientName;

    /** Email subject line */
    private String subject;

    /** Full message body */
    private String message;

    /**
     * Type of notification — used for categorisation/logging.
     * Values: MAINTENANCE_FLAG_RAISED, FLAG_ASSIGNED, FLAG_PROGRESS,
     *         FLAG_RESOLVED, TRIP_APPROVED, TRIP_REJECTED
     */
    private String type;

    private LocalDateTime occurredAt;
}
