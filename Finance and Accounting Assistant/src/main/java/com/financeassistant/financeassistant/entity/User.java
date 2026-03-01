package com.financeassistant.financeassistant.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/entity/User.java
 *
 * CHANGES:
 *  - Added FREE as the new default subscription status
 *  - Removed auto-start of trial from @PrePersist (trial is now opt-in)
 *  - Default subscriptionStatus is now FREE (not TRIAL)
 *  - Added aiChatsUsedToday + aiChatResetDate for daily AI chat limits
 *  - Updated isSubscriptionActive() to handle FREE tier
 *  - Added helper methods: getTier(), getAiChatDailyLimit(), canUseAiChat()
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
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.FREE;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Column(name = "razorpay_subscription_id", length = 255)
    private String razorpaySubscriptionId;

    // ── AI chat daily tracking ────────────────────────────────────────────────
    @Column(name = "ai_chats_used_today", nullable = false)
    private int aiChatsUsedToday = 0;

    @Column(name = "ai_chat_reset_date")
    private LocalDate aiChatResetDate;

    // ── Subscription Status Enum ──────────────────────────────────────────────
    public enum SubscriptionStatus {
        FREE,       // Default — permanent free tier with limited features
        TRIAL,      // 5-day premium trial (explicitly started by user)
        ACTIVE,     // Paid subscriber
        EXPIRED,    // Trial ended, reverts to FREE limitations
        CANCELLED   // Was subscriber, reverts to FREE limitations
    }

    // ── Business logic helpers ────────────────────────────────────────────────

    /**
     * Returns true if this user can call the business APIs.
     * FREE, EXPIRED, CANCELLED users can still access the app (with limits).
     * Only checked for TRIAL expiry and ACTIVE subscription expiry.
     */
    public boolean isSubscriptionActive() {
        switch (subscriptionStatus) {
            case ACTIVE:
                return subscriptionExpiresAt == null
                        || Instant.now().isBefore(subscriptionExpiresAt);
            case TRIAL:
                if (trialStartedAt == null) return true;
                return Instant.now().isBefore(trialStartedAt.plus(5, ChronoUnit.DAYS));
            case FREE:
            case EXPIRED:
            case CANCELLED:
                return true; // Always "active" — just with limited features
            default:
                return false;
        }
    }

    /**
     * Returns true if this user has access to premium features.
     * Only TRIAL (within period) and ACTIVE (within expiry) have premium access.
     */
    public boolean hasPremiumAccess() {
        switch (subscriptionStatus) {
            case ACTIVE:
                return subscriptionExpiresAt == null
                        || Instant.now().isBefore(subscriptionExpiresAt);
            case TRIAL:
                if (trialStartedAt == null) return false;
                return Instant.now().isBefore(trialStartedAt.plus(5, ChronoUnit.DAYS));
            default:
                return false;
        }
    }

    /**
     * Returns the effective tier string for frontend consumption.
     */
    public String getEffectiveTier() {
        switch (subscriptionStatus) {
            case ACTIVE:
                if (subscriptionExpiresAt == null || Instant.now().isBefore(subscriptionExpiresAt))
                    return "ACTIVE";
                return "FREE"; // Expired ACTIVE → FREE
            case TRIAL:
                if (trialStartedAt != null && Instant.now().isBefore(trialStartedAt.plus(5, ChronoUnit.DAYS)))
                    return "TRIAL";
                return "FREE"; // Expired TRIAL → FREE
            case FREE:
            case EXPIRED:
            case CANCELLED:
            default:
                return "FREE";
        }
    }

    /**
     * Returns days remaining on free trial (0 if not on trial or expired).
     */
    public long trialDaysRemaining() {
        if (subscriptionStatus != SubscriptionStatus.TRIAL) return 0L;
        if (trialStartedAt == null) return 5L;
        Instant expiry    = trialStartedAt.plus(5, ChronoUnit.DAYS);
        long    remaining = Instant.now().until(expiry, ChronoUnit.DAYS);
        return Math.max(0L, remaining);
    }

    /**
     * Returns the daily AI chat limit based on tier.
     * FREE/EXPIRED/CANCELLED: 3/day
     * TRIAL: 10/day
     * ACTIVE: 50/day
     */
    public int getAiChatDailyLimit() {
        String tier = getEffectiveTier();
        switch (tier) {
            case "ACTIVE": return 50;
            case "TRIAL":  return 10;
            default:       return 3;
        }
    }

    /**
     * Returns AI chats remaining today for this user.
     * Resets the counter if it's a new day.
     */
    public int getAiChatsRemainingToday() {
        LocalDate today = LocalDate.now();
        if (aiChatResetDate == null || !aiChatResetDate.equals(today)) {
            return getAiChatDailyLimit(); // New day — full allowance
        }
        return Math.max(0, getAiChatDailyLimit() - aiChatsUsedToday);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        // ✅ FIX: Do NOT auto-start trial here.
        // Trial is now explicitly started by the user via /api/v1/subscription/start-trial
        // Default status is FREE (set by field initializer above)
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

    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return true; }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public Long getId()                                    { return id; }
    public String getEmail()                               { return email; }
    public void setEmail(String email)                     { this.email = email; }
    public void setPassword(String password)               { this.password = password; }
    public String getRole()                                { return role; }
    public void setRole(String role)                       { this.role = role; }
    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public Instant getTrialStartedAt()                     { return trialStartedAt; }
    public void setTrialStartedAt(Instant t)               { this.trialStartedAt = t; }
    public SubscriptionStatus getSubscriptionStatus()      { return subscriptionStatus; }
    public void setSubscriptionStatus(SubscriptionStatus s){ this.subscriptionStatus = s; }
    public Instant getSubscriptionExpiresAt()              { return subscriptionExpiresAt; }
    public void setSubscriptionExpiresAt(Instant e)        { this.subscriptionExpiresAt = e; }
    public String getRazorpaySubscriptionId()              { return razorpaySubscriptionId; }
    public void setRazorpaySubscriptionId(String id)       { this.razorpaySubscriptionId = id; }
    public int getAiChatsUsedToday()                       { return aiChatsUsedToday; }
    public void setAiChatsUsedToday(int n)                 { this.aiChatsUsedToday = n; }
    public LocalDate getAiChatResetDate()                  { return aiChatResetDate; }
    public void setAiChatResetDate(LocalDate d)            { this.aiChatResetDate = d; }
}