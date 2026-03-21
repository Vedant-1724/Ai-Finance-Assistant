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
    private String role;
    private String subscriptionStatus;  // Effective tier: FREE, TRIAL, ACTIVE
    private long   trialDaysRemaining;
    private int    aiChatsRemaining;
    private int    aiChatDailyLimit;
    private boolean hasPremiumAccess;
    private boolean canManageBilling;

    public AuthResponse() {}

    public AuthResponse(String token, Long companyId, String email, String role,
                        String subscriptionStatus, long trialDaysRemaining, int aiChatsRemaining,
                        int aiChatDailyLimit, boolean hasPremiumAccess, boolean canManageBilling) {
        this.token              = token;
        this.companyId          = companyId;
        this.email              = email;
        this.role               = role;
        this.subscriptionStatus = subscriptionStatus;
        this.trialDaysRemaining = trialDaysRemaining;
        this.aiChatsRemaining   = aiChatsRemaining;
        this.aiChatDailyLimit   = aiChatDailyLimit;
        this.hasPremiumAccess   = hasPremiumAccess;
        this.canManageBilling   = canManageBilling;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public String getToken()                            { return token; }
    public void   setToken(String token)                { this.token = token; }
    public Long   getCompanyId()                        { return companyId; }
    public void   setCompanyId(Long id)                 { this.companyId = id; }
    public String getEmail()                            { return email; }
    public void   setEmail(String email)                { this.email = email; }
    public String getRole()                             { return role; }
    public void   setRole(String role)                  { this.role = role; }
    public String getSubscriptionStatus()               { return subscriptionStatus; }
    public void   setSubscriptionStatus(String s)       { this.subscriptionStatus = s; }
    public long   getTrialDaysRemaining()               { return trialDaysRemaining; }
    public void   setTrialDaysRemaining(long d)         { this.trialDaysRemaining = d; }
    public int    getAiChatsRemaining()                 { return aiChatsRemaining; }
    public void   setAiChatsRemaining(int n)            { this.aiChatsRemaining = n; }
    public int    getAiChatDailyLimit()                 { return aiChatDailyLimit; }
    public void   setAiChatDailyLimit(int n)            { this.aiChatDailyLimit = n; }
    public boolean getHasPremiumAccess()                { return hasPremiumAccess; }
    public void   setHasPremiumAccess(boolean value)    { this.hasPremiumAccess = value; }
    public boolean getCanManageBilling()                { return canManageBilling; }
    public void   setCanManageBilling(boolean value)    { this.canManageBilling = value; }
}
