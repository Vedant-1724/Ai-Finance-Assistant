package com.financeassistant.financeassistant.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Login request DTO with validation.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email too long")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 128, message = "Invalid password")
    private String password;
}