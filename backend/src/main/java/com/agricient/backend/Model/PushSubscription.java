package com.agricient.backend.Model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One browser or phone that agreed to receive push notifications for a farmer. */
@Entity
@Table(name = "push_subscription", uniqueConstraints = @UniqueConstraint(
        name = "uk_push_endpoint", columnNames = { "endpointHash" }))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    // the full address is long, so uniqueness is enforced on its hash
    @Column(name = "endpointHash", nullable = false, length = 64)
    private String endpointHash;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    private LocalDateTime createdAt;

    public PushSubscription() {
    }

    public PushSubscription(Long userId, String endpointHash, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpointHash = endpointHash;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpointHash() { return endpointHash; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
