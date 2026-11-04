package com.agricient.backend.Model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** One sign-in. Only a hash of the token is stored, so a database leak does not expose live tokens. */
@Entity
public class AuthSession {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Long userId;

    private LocalDateTime expiresAt;

    public AuthSession() {
    }

    public AuthSession(String tokenHash, Long userId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public Long getUserId() { return userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
