package com.financeassistant.financeassistant.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/entity/User.java
 *
 * CHANGES:
 *  - Added trial_started_at, subscription_status, subscription_expires_at,
 *    razorpay_subscription_id columns (backed by V4 Flyway migration)
 *  - Added SubscriptionStatus enum
 *  - Added isSubscriptionActive() helper used by SubscriptionFilter
 *  - Added trialDaysRemaining() helper used by PaymentController
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    // ── Primary key ───────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Auth fields ───────────────────────────────────────────────────────────
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String role = "USER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Subscription fields ───────────────────────────────────────────────────
    @Column(name = "trial_started_at")
    private Instant trialStartedAt;

    @Column(name = "subscription_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Column(name = "razorpay_subscription_id", length = 255)
    private String razorpaySubscriptionId;

    // ── Subscription Status Enum ──────────────────────────────────────────────
    public enum SubscriptionStatus {
        TRIAL,      // 5-day free trial
        ACTIVE,     // Paid subscriber
        EXPIRED,    // Trial ended, not subscribed
        CANCELLED   // Was subscriber, cancelled
    }

    // ── Business logic helpers ────────────────────────────────────────────────

    /**
     * Returns true if this user is currently allowed to access the app.
     * Called by SubscriptionFilter on every API request.
     */
    public boolean isSubscriptionActive() {
        switch (subscriptionStatus) {
            case ACTIVE:
                return subscriptionExpiresAt == null
                        || Instant.now().isBefore(subscriptionExpiresAt);
            case TRIAL:
                if (trialStartedAt == null) return true; // trial not yet started
                return Instant.now().isBefore(trialStartedAt.plus(5, ChronoUnit.DAYS));
            default:
                return false;
        }
    }

    /**
     * Returns days remaining on free trial (0 if expired or on paid plan).
     */
    public long trialDaysRemaining() {
        if (subscriptionStatus != SubscriptionStatus.TRIAL) return 0L;
        if (trialStartedAt == null) return 5L;
        Instant expiry    = trialStartedAt.plus(5, ChronoUnit.DAYS);
        long    remaining = Instant.now().until(expiry, ChronoUnit.DAYS);
        return Math.max(0L, remaining);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.trialStartedAt == null) {
            this.trialStartedAt = Instant.now();
        }
    }

    // ── Constructors ──────────────────────────────────────────────────────────
    public User() {}

    public User(String email, String password, String role) {
        this.email    = email;
        this.password = password;
        this.role     = role;
    }

    // ── UserDetails interface ─────────────────────────────────────────────────
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return password; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public Long              getId()                                      { return id; }
    public void              setId(Long id)                               { this.id = id; }
    public String            getEmail()                                   { return email; }
    public void              setEmail(String email)                       { this.email = email; }
    public void              setPassword(String password)                 { this.password = password; }
    public String            getRole()                                    { return role; }
    public void              setRole(String role)                         { this.role = role; }
    public LocalDateTime     getCreatedAt()                               { return createdAt; }
    public Instant           getTrialStartedAt()                          { return trialStartedAt; }
    public void              setTrialStartedAt(Instant t)                 { this.trialStartedAt = t; }
    public SubscriptionStatus getSubscriptionStatus()                     { return subscriptionStatus; }
    public void              setSubscriptionStatus(SubscriptionStatus s)  { this.subscriptionStatus = s; }
    public Instant           getSubscriptionExpiresAt()                   { return subscriptionExpiresAt; }
    public void              setSubscriptionExpiresAt(Instant t)          { this.subscriptionExpiresAt = t; }
    public String            getRazorpaySubscriptionId()                  { return razorpaySubscriptionId; }
    public void              setRazorpaySubscriptionId(String s)          { this.razorpaySubscriptionId = s; }
}
