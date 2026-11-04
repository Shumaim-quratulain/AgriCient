package com.agricient.backend.Model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_alert_channel", columnNames = { "alertId", "channel" }))
public class Notification {

    public static final String PENDING = "PENDING";
    public static final String SIMULATED = "SIMULATED";   // logged only, nothing was sent
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "alertId", nullable = false)
    private Long alertId;

    @Column(name = "channel", nullable = false, length = 10)
    private String channel;              // "SMS"

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(length = 200)
    private String error;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public Notification() {
    }

    public Notification(Long userId, Long alertId, String channel, String message) {
        this.userId = userId;
        this.alertId = alertId;
        this.channel = channel;
        this.message = message;
        this.status = PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markDone(String status, String error) {
        this.status = status;
        this.error = error;
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAlertId() { return alertId; }
    public String getChannel() { return channel; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
}