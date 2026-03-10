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

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Transactional
    public RegistrationResult register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = new User(
                req.getEmail().toLowerCase().trim(),
                passwordEncoder.encode(req.getPassword()),
                "USER");
        User savedUser = userRepository.save(user);

        Company company = new Company();
        company.setOwnerId(savedUser.getId());
        company.setName(req.getCompanyName());
        company.setCurrency("INR");
        Company savedCompany = companyRepository.save(company);

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken tokenEntity = new EmailVerificationToken(rawToken, savedUser,
                LocalDateTime.now().plusHours(24));
        tokenRepository.save(tokenEntity);
        emailService.sendEmailVerification(savedUser.getEmail(), rawToken);

        log.info("Registered new FREE user (Unverified): {}", savedUser.getEmail());
        return new RegistrationResult(buildAuthResponse("", savedCompany.getId(), savedUser), rawToken);
    }

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

        Company company = companyRepository.findFirstByOwnerId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No company found for user"));

        String token = jwtUtil.generateToken(user.getEmail(), company.getId());
        log.info("Login: {}", user.getEmail());
        return buildAuthResponse(token, company.getId(), user);
    }

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

        tokenRepository.delete(verificationToken);
        log.info("Email verified successfully for: {}", user.getEmail());
    }

    @Transactional
    public PasswordResetInitiationResult forgotPassword(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim()).orElse(null);
        if (user == null) {
            return new PasswordResetInitiationResult(false, null);
        }

        resetTokenRepository.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken tokenEntity = new PasswordResetToken(rawToken, user, LocalDateTime.now().plusHours(1));
        resetTokenRepository.save(tokenEntity);

        emailService.sendPasswordReset(user.getEmail(), rawToken);
        log.info("Password reset requested for: {}", user.getEmail());
        return new PasswordResetInitiationResult(true, rawToken);
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

        resetTokenRepository.delete(resetToken);
        log.info("Password was reset successfully for: {}", user.getEmail());
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must differ from your current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed successfully for: {}", user.getEmail());
    }

    private AuthResponse buildAuthResponse(String token, Long companyId, User user) {
        return new AuthResponse(
                token,
                companyId,
                user.getEmail(),
                user.getEffectiveTier(),
                user.trialDaysRemaining(),
                user.getAiChatsRemainingToday());
    }

    public record RegistrationResult(AuthResponse authResponse, String verificationToken) {
    }

    public record PasswordResetInitiationResult(boolean accountFound, String resetToken) {
    }
}
