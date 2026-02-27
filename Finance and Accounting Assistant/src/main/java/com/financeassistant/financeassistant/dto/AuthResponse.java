package com.financeassistant.financeassistant.dto;

/**
 * Returned by POST /api/v1/auth/login and POST /api/v1/auth/register.
 *
 * The frontend stores the token in localStorage and uses companyId
 * to replace the previous hardcoded companyId = 1.
 */
public class AuthResponse {

    private String token;
    private Long   companyId;
    private String email;

    public AuthResponse() {}

    public AuthResponse(String token, Long companyId, String email) {
        this.token     = token;
        this.companyId = companyId;
        this.email     = email;
    }

    public String getToken()               { return token; }
    public void   setToken(String token)   { this.token = token; }
    public Long   getCompanyId()           { return companyId; }
    public void   setCompanyId(Long id)    { this.companyId = id; }
    public String getEmail()               { return email; }
    public void   setEmail(String email)   { this.email = email; }
}
