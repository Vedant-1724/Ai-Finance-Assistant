package com.financeassistant.financeassistant.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Registration request DTO with strong validation.
 * Password policy meets PCI-DSS requirements (needed for Razorpay payments).
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email too long")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, and one number"
    )
    private String password;

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100, message = "Company name must be 2–100 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9 .,'&()-]+$",
        message = "Company name contains invalid characters"
    )
    private String companyName;
}