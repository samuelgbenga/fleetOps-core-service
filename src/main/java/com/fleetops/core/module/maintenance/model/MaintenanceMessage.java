package com.fleetops.core.module.maintenance.model;

import com.fleetops.core.module.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_flag_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flag_id", nullable = false)
    private MaintenanceFlag flag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "sender_role", nullable = false)
    private String senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = LocalDateTime.now();
    }
}
