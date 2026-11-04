package com.agricient.backend.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** The latest weather-risk forecast for one ~1 km area. One row per area, updated in place. */
@Entity
public class PlaceForecast {

    @Id
    @Column(name = "placeKey", length = 40)
    private String placeKey;

    private double latitude;
    private double longitude;

    @Column(name = "predictedFor")
    private LocalDate predictedFor;

    private double predictedRisk;
    private double riskToday;
    private LocalDateTime updatedAt;

    public PlaceForecast() {
    }

    public PlaceForecast(String placeKey, double latitude, double longitude) {
        this.placeKey = placeKey;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void update(LocalDate predictedFor, double predictedRisk, double riskToday) {
        this.predictedFor = predictedFor;
        this.predictedRisk = predictedRisk;
        this.riskToday = riskToday;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPlaceKey() { return placeKey; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public LocalDate getPredictedFor() { return predictedFor; }
    public double getPredictedRisk() { return predictedRisk; }
    public double getRiskToday() { return riskToday; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}