package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.AuthResponse;
import com.financeassistant.financeassistant.dto.LoginRequest;
import com.financeassistant.financeassistant.dto.RegisterRequest;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.EmailVerificationToken;
import com.financeassistant.financeassistant.entity.PasswordResetToken;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.EmailVerificationTokenRepository;
import com.financeassistant.financeassistant.repository.PasswordResetTokenRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import com.financeassistant.financeassistant.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailAlertService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ── UserDetailsService ────────────────────────────────────────────────────
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // ── Register ──────────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        // Create user — defaults to FREE tier (no trial auto-start)
        User user = new User(
                req.getEmail().toLowerCase().trim(),
                passwordEncoder.encode(req.getPassword()),
                "USER");
        User savedUser = userRepository.save(user);

        // Create company
        Company company = new Company();
        company.setOwnerId(savedUser.getId());
        company.setName(req.getCompanyName());
        company.setCurrency("INR");
        Company savedCompany = companyRepository.save(company);

        // ✅ Generate and Send Verification Email (Token expires in 24 hours)
        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken tokenEntity = new EmailVerificationToken(rawToken, savedUser,
                LocalDateTime.now().plusHours(24));
        tokenRepository.save(tokenEntity);
        emailService.sendEmailVerification(savedUser.getEmail(), rawToken);

        // ✅ User is not logged in immediately. We don't dispatch a JWT token here
        // anymore.
        // The controller returning 201 will let the React front-end know to show a
        // "Check your email" screen.
        log.info("Registered new FREE user (Unverified): {}", savedUser.getEmail());
        return buildAuthResponse("", savedCompany.getId(), savedUser);
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("EMAIL_UNVERIFIED");
        }

        // ✅ FIX: use findFirstByOwnerId (not findByOwnerId)
        Company company = companyRepository.findFirstByOwnerId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No company found for user"));

        // ✅ FIX: generateToken needs TWO args (email, companyId)
        String token = jwtUtil.generateToken(user.getEmail(), company.getId());
        log.info("Login: {}", user.getEmail());
        return buildAuthResponse(token, company.getId(), user);
    }

    // ── Email Verification Endpoint ───────────────────────────────────────────
    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token."));

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(verificationToken);
            throw new IllegalArgumentException("Verification token has expired. Please request a new one.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete the token so it cannot be reused
        tokenRepository.delete(verificationToken);
        log.info("Email verified successfully for: {}", user.getEmail());
    }

    // ── Password Reset Endpoints ──────────────────────────────────────────────
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("If this email exists, a reset link will be sent."));

        // Delete any existing reset token for this user
        resetTokenRepository.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken tokenEntity = new PasswordResetToken(rawToken, user, LocalDateTime.now().plusHours(1));
        resetTokenRepository.save(tokenEntity);

        emailService.sendPasswordReset(user.getEmail(), rawToken);
        log.info("Password reset requested for: {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            resetTokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Reset token has expired. Please request a new one.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Discard token so it cannot be used again
        resetTokenRepository.delete(resetToken);
        log.info("Password was reset successfully for: {}", user.getEmail());
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private AuthResponse buildAuthResponse(String token, Long companyId, User user) {
        return new AuthResponse(
                token,
                companyId,
                user.getEmail(),
                user.getEffectiveTier(),
                user.trialDaysRemaining(),
                user.getAiChatsRemainingToday());
    }
}