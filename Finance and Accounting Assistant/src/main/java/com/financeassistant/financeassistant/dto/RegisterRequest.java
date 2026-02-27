package com.financeassistant.financeassistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Company name is required")
    private String companyName;

    public RegisterRequest() {}
    public RegisterRequest(String email, String password, String companyName) {
        this.email       = email;
        this.password    = password;
        this.companyName = companyName;
    }

    public String getEmail()                     { return email; }
    public void   setEmail(String email)         { this.email = email; }
    public String getPassword()                  { return password; }
    public void   setPassword(String p)          { this.password = p; }
    public String getCompanyName()               { return companyName; }
    public void   setCompanyName(String name)    { this.companyName = name; }
}
