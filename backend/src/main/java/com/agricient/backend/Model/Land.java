package com.agricient.backend.Model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Land {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 80)
    private String village;

    private Double areaAcres;
    private double latitude;
    private double longitude;
    private Double accuracyMeters;
    private LocalDateTime createdAt;

    public Land() {
    }

    public Land(Long ownerId, String name, String village, Double areaAcres,
            double latitude, double longitude, Double accuracyMeters) {
        this.ownerId = ownerId;
        this.name = name;
        this.village = village;
        this.areaAcres = areaAcres;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getVillage() { return village; }
    public Double getAreaAcres() { return areaAcres; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
