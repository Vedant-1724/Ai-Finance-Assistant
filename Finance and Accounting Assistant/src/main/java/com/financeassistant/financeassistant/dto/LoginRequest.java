package com.financeassistant.financeassistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public LoginRequest() {}
    public LoginRequest(String email, String password) {
        this.email    = email;
        this.password = password;
    }

    public String getEmail()              { return email; }
    public void   setEmail(String email)  { this.email = email; }
    public String getPassword()           { return password; }
    public void   setPassword(String p)   { this.password = p; }
}
