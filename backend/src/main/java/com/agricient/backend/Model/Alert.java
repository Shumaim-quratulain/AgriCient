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
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_alert_site_day", columnNames = { "site", "forecastFor" }))
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String site;

    @Column(name = "forecastFor", nullable = false)
    private LocalDate forecastFor;

    @Column(nullable = false, length = 20)
    private String level;            // NONE, WATCH or WARNING

    private double predictedRisk;
    private String leafLabel;        // null when no photo was used
    private Float leafConfidence;

    @Column(length = 400)
    private String reason;

    private LocalDateTime createdAt;

    public Alert() {
    }

    public Alert(String site, LocalDate forecastFor, String level, double predictedRisk,
            String leafLabel, Float leafConfidence, String reason) {
        this.site = site;
        this.forecastFor = forecastFor;
        update(level, predictedRisk, leafLabel, leafConfidence, reason);
    }

    public void update(String level, double predictedRisk, String leafLabel,
            Float leafConfidence, String reason) {
        this.level = level;
        this.predictedRisk = predictedRisk;
        this.leafLabel = leafLabel;
        this.leafConfidence = leafConfidence;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getSite() { return site; }
    public LocalDate getForecastFor() { return forecastFor; }
    public String getLevel() { return level; }
    public double getPredictedRisk() { return predictedRisk; }
    public String getLeafLabel() { return leafLabel; }
    public Float getLeafConfidence() { return leafConfidence; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}