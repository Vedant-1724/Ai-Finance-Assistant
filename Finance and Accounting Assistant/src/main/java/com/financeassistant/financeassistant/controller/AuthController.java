package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.config.GlobalExceptionHandler.RateLimitException;
import com.financeassistant.financeassistant.dto.AuthResponse;
import com.financeassistant.financeassistant.dto.ChangePasswordRequest;
import com.financeassistant.financeassistant.dto.LoginRequest;
import com.financeassistant.financeassistant.dto.RegisterRequest;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.security.JwtUtil;
import com.financeassistant.financeassistant.security.LoginRateLimiter;
import com.financeassistant.financeassistant.security.TokenBlacklistService;
import com.financeassistant.financeassistant.service.AuthService;
import com.financeassistant.financeassistant.service.EmailAlertService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final TokenBlacklistService blacklistService;
    private final JwtUtil jwtUtil;
    private final CompanyRepository companyRepository;
    private final EmailAlertService emailAlertService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {

        String ip = getClientIp(httpReq);

        if (!rateLimiter.tryConsumeLogin(ip)) {
            log.warn("Rate limit exceeded for login from IP: {}", ip);
            throw new RateLimitException("Too many login attempts. Please wait 1 minute.");
        }

        try {
            AuthResponse response = authService.login(req);
            log.info("Login success: {} from IP: {}", req.getEmail(), ip);

            ResponseCookie cookie = ResponseCookie.from("jwt_token", response.getToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(30L * 24 * 60 * 60)
                    .build();
            httpResp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            log.warn("Login failed for {} from IP: {}", req.getEmail(), ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        } catch (IllegalStateException e) {
            if ("EMAIL_UNVERIFIED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "EMAIL_UNVERIFIED"));
            }
            log.error("Login state error for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        } catch (Exception e) {
            log.error("Login error for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {

        String ip = getClientIp(httpReq);

        if (!rateLimiter.tryConsumeRegister(ip)) {
            log.warn("Rate limit exceeded for register from IP: {}", ip);
            throw new RateLimitException("Too many registration attempts. Please wait 10 minutes.");
        }

        try {
            AuthService.RegistrationResult result = authService.register(req);
            log.info("Registration success: {} from IP: {}", req.getEmail(), ip);

            ResponseCookie cookie = ResponseCookie.from("jwt_token", "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(0)
                    .build();
            httpResp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", emailAlertService.isMailEnabled()
                    ? "Registered. Please check your email to verify your account."
                    : "Registered. Email delivery is disabled in this environment, so use the verification link below.");
            body.put("emailDeliveryEnabled", emailAlertService.isMailEnabled());
            if (emailAlertService.shouldExposeActionLinksWhenDisabled()) {
                body.put("verificationUrl", emailAlertService.buildVerificationUrl(result.verificationToken()));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Register error for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Registration failed. Please try again."));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now login."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody com.financeassistant.financeassistant.dto.ForgotPasswordRequest req) {
        try {
            AuthService.PasswordResetInitiationResult result = authService.forgotPassword(req.getEmail());
            Map<String, Object> body = new LinkedHashMap<>();
            if (!emailAlertService.isMailEnabled()) {
                body.put("message", result.accountFound() && emailAlertService.shouldExposeActionLinksWhenDisabled()
                        ? "Email delivery is disabled in this environment. Use the reset link below if this account exists."
                        : "Email delivery is disabled in this environment. If that email exists, contact support or enable mail to complete password resets.");
            } else {
                body.put("message", "If that email exists, a password reset link has been sent.");
            }
            body.put("emailDeliveryEnabled", emailAlertService.isMailEnabled());
            if (result.accountFound() && emailAlertService.shouldExposeActionLinksWhenDisabled()) {
                body.put("resetUrl", emailAlertService.buildResetUrl(result.resetToken()));
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Forgot password error for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "message", "If that email exists, a password reset link has been sent.",
                    "emailDeliveryEnabled", emailAlertService.isMailEnabled()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody com.financeassistant.financeassistant.dto.ResetPasswordRequest req) {
        try {
            authService.resetPassword(req.getToken(), req.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password has been successfully reset."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Reset password error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset password. Please try again."));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest req) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        try {
            authService.changePassword(user.getId(), req.getCurrentPassword(), req.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password updated successfully. Please log in again."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Change password error for {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to change password. Please try again."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(value = "jwt_token", required = false) String token,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {

        if (token != null && !token.isBlank()) {
            long expiryMs = jwtUtil.getExpiryMs(token) - System.currentTimeMillis();

            if (expiryMs > 0) {
                blacklistService.blacklist(token, expiryMs);
            }

            log.info("User logged out from IP: {}", getClientIp(httpReq));
        }

        ResponseCookie cookie = ResponseCookie.from("jwt_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();
        httpResp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        Long companyId = companyRepository.findFirstByOwnerId(user.getId())
                .map(company -> company.getId())
                .orElse(0L);

        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "companyId", companyId,
                "subscriptionStatus", user.getEffectiveTier(),
                "trialDaysRemaining", user.trialDaysRemaining(),
                "aiChatsRemaining", user.getAiChatsRemainingToday(),
                "hasPremiumAccess", user.hasPremiumAccess()));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
