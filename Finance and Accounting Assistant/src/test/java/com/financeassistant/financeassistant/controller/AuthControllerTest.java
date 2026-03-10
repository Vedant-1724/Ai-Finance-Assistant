package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.AuthResponse;
import com.financeassistant.financeassistant.dto.ForgotPasswordRequest;
import com.financeassistant.financeassistant.dto.RegisterRequest;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.security.JwtUtil;
import com.financeassistant.financeassistant.security.LoginRateLimiter;
import com.financeassistant.financeassistant.security.TokenBlacklistService;
import com.financeassistant.financeassistant.service.AuthService;
import com.financeassistant.financeassistant.service.EmailAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private LoginRateLimiter rateLimiter;

    @Mock
    private TokenBlacklistService blacklistService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private EmailAlertService emailAlertService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                authService,
                rateLimiter,
                blacklistService,
                jwtUtil,
                companyRepository,
                emailAlertService);
    }

    @Test
    void registerReturnsVerificationUrlWhenMailIsDisabled() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("owner@example.com");
        request.setPassword("Password1");
        request.setCompanyName("Acme Pvt Ltd");

        when(rateLimiter.tryConsumeRegister(anyString())).thenReturn(true);
        when(authService.register(any(RegisterRequest.class))).thenReturn(
                new AuthService.RegistrationResult(
                        new AuthResponse("", 1L, "owner@example.com", "FREE", 0, 0),
                        "verify-token"));
        when(emailAlertService.isMailEnabled()).thenReturn(false);
        when(emailAlertService.shouldExposeActionLinksWhenDisabled()).thenReturn(true);
        when(emailAlertService.buildVerificationUrl("verify-token"))
                .thenReturn("http://localhost:5173/verify-email?token=verify-token");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<?> response = authController.register(request, servletRequest, servletResponse);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("emailDeliveryEnabled"));
        assertEquals("http://localhost:5173/verify-email?token=verify-token", body.get("verificationUrl"));
        assertNotNull(servletResponse.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void forgotPasswordReturnsResetUrlWhenMailIsDisabled() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("owner@example.com");

        when(authService.forgotPassword("owner@example.com"))
                .thenReturn(new AuthService.PasswordResetInitiationResult(true, "reset-token"));
        when(emailAlertService.isMailEnabled()).thenReturn(false);
        when(emailAlertService.shouldExposeActionLinksWhenDisabled()).thenReturn(true);
        when(emailAlertService.buildResetUrl("reset-token"))
                .thenReturn("http://localhost:5173/reset-password?token=reset-token");

        ResponseEntity<?> response = authController.forgotPassword(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertFalse((Boolean) body.get("emailDeliveryEnabled"));
        assertEquals("http://localhost:5173/reset-password?token=reset-token", body.get("resetUrl"));
    }
}
