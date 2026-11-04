package com.agricient.backend.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_alert", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_alert_place_day", columnNames = { "userId", "placeKey", "forecastFor" }))
public class UserAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "placeKey", nullable = false, length = 40)
    private String placeKey;

    @Column(nullable = false, length = 80)
    private String placeName;

    private Long landId;                 // null for the home location

    @Column(name = "forecastFor", nullable = false)
    private LocalDate forecastFor;

    @Column(nullable = false, length = 20)
    private String level;                // WATCH or WARNING

    private double predictedRisk;

    @Column(length = 400)
    private String reason;

    private LocalDateTime createdAt;
    private LocalDateTime readAt;        // null until the farmer opens it

    public UserAlert() {
    }

    public UserAlert(Long userId, String placeKey, String placeName, Long landId,
            LocalDate forecastFor, String level, double predictedRisk, String reason) {
        this.userId = userId;
        this.placeKey = placeKey;
        this.placeName = placeName;
        this.landId = landId;
        this.forecastFor = forecastFor;
        this.createdAt = LocalDateTime.now();
        update(level, predictedRisk, reason);
    }

    /** Re-evaluation of the same place and day updates this row instead of adding another. */
    public void update(String level, double predictedRisk, String reason) {
        this.level = level;
        this.predictedRisk = predictedRisk;
        this.reason = reason;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
public Long getUserId() { return userId; }
public String getPlaceKey() { return placeKey; }
public String getPlaceName() { return placeName; }
public Long getLandId() { return landId; }
public LocalDate getForecastFor() { return forecastFor; }
public String getLevel() { return level; }
public double getPredictedRisk() { return predictedRisk; }
public String getReason() { return reason; }
public LocalDateTime getCreatedAt() { return createdAt; }
public LocalDateTime getReadAt() { return readAt; }
}