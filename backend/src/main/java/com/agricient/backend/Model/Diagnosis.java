package com.agricient.backend.Model;

import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Diagnosis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String imageName;
    private String topLabel;
    private float confidence;
    private LocalDateTime createdAt;
    private Long ownerId;
    private Long landId;

    public Diagnosis() {
    }
    public Long getId() {
    return id;
}

public String getImageName() {
    return imageName;
}

public String getTopLabel() {
    return topLabel;
}

public float getConfidence() {
    return confidence;
}

public LocalDateTime getCreatedAt() {
    return createdAt;
}
public Long getOwnerId() {
    return ownerId;
}

public Long getLandId() {
    return landId;
}

public Diagnosis(String imageName, String topLabel, float confidence, Long ownerId, Long landId) {
    this(imageName, topLabel, confidence);
    this.ownerId = ownerId;
    this.landId = landId;
}
public Diagnosis(String imageName, String topLabel, float confidence) {
    this.imageName = imageName;
    this.topLabel = topLabel;
    this.confidence = confidence;
    this.createdAt = LocalDateTime.now();
}
}
