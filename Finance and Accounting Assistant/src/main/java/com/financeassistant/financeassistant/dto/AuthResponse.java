package com.financeassistant.financeassistant.dto;

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/dto/AuthResponse.java
 *
 * CHANGES:
 *  - Added subscriptionStatus (effective tier: FREE/TRIAL/ACTIVE)
 *  - Added trialDaysRemaining
 *  - Added aiChatsRemaining (today's remaining AI chat quota)
 *  Frontend stores all these in AuthContext to gate feature access.
 */
public class AuthResponse {

    private String token;
    private Long   companyId;
    private String email;
    private String subscriptionStatus;  // Effective tier: FREE, TRIAL, ACTIVE
    private long   trialDaysRemaining;
    private int    aiChatsRemaining;

    public AuthResponse() {}

    public AuthResponse(String token, Long companyId, String email,
                        String subscriptionStatus, long trialDaysRemaining, int aiChatsRemaining) {
        this.token              = token;
        this.companyId          = companyId;
        this.email              = email;
        this.subscriptionStatus = subscriptionStatus;
        this.trialDaysRemaining = trialDaysRemaining;
        this.aiChatsRemaining   = aiChatsRemaining;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public String getToken()                            { return token; }
    public void   setToken(String token)                { this.token = token; }
    public Long   getCompanyId()                        { return companyId; }
    public void   setCompanyId(Long id)                 { this.companyId = id; }
    public String getEmail()                            { return email; }
    public void   setEmail(String email)                { this.email = email; }
    public String getSubscriptionStatus()               { return subscriptionStatus; }
    public void   setSubscriptionStatus(String s)       { this.subscriptionStatus = s; }
    public long   getTrialDaysRemaining()               { return trialDaysRemaining; }
    public void   setTrialDaysRemaining(long d)         { this.trialDaysRemaining = d; }
    public int    getAiChatsRemaining()                 { return aiChatsRemaining; }
    public void   setAiChatsRemaining(int n)            { this.aiChatsRemaining = n; }
}