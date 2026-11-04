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
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_user_login", columnNames = "login"))
public class AppUser {

    public static final String FARMER = "FARMER";
    public static final String OFFICER = "OFFICER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** Email or phone number used to sign in. Stored lower-case. */
    @Column(nullable = false, length = 120)
    private String login;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 80)
    private String locality;

    /** Optional mobile number for SMS alerts: digits with an optional leading +. */
    @Column(length = 20)
    private String phone;

    /** True only if the farmer agreed to receive texts. Never true without a phone number. */
    private boolean smsConsent;

    /** Home location chosen at registration. Null only for accounts created before this field existed. */
    private Double latitude;
    private Double longitude;

    private LocalDateTime createdAt;

    public AppUser() {
    }

    public AppUser(String name, String login, String passwordHash, String role, String locality,
            Double latitude, Double longitude) {
        this.name = name;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.locality = locality;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLogin() { return login; }
    public String getRole() { return role; }
    public String getLocality() { return locality; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getPhone() { return phone; }
    public boolean isSmsConsent() { return smsConsent; }

    /** Sets both together, so consent can never be true without a number. */
    public void updateSms(String phone, boolean consent) {
        this.phone = phone;
        this.smsConsent = consent && phone != null;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() { return passwordHash; }
}
