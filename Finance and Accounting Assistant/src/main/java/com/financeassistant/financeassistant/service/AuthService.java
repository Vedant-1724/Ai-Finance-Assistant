package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.AuthResponse;
import com.financeassistant.financeassistant.dto.LoginRequest;
import com.financeassistant.financeassistant.dto.RegisterRequest;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository    userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtUtil           jwtUtil;

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
                "USER"
        );
        User savedUser = userRepository.save(user);

        // Create company
        Company company = new Company();
        company.setOwnerId(savedUser.getId());
        company.setName(req.getCompanyName());
        company.setCurrency("INR");
        Company savedCompany = companyRepository.save(company);

        // ✅ FIX: generateToken needs TWO args (email, companyId)
        String token = jwtUtil.generateToken(savedUser.getEmail(), savedCompany.getId());
        log.info("Registered new FREE user: {}", savedUser.getEmail());
        return buildAuthResponse(token, savedCompany.getId(), savedUser);
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // ✅ FIX: use findFirstByOwnerId (not findByOwnerId)
        Company company = companyRepository.findFirstByOwnerId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No company found for user"));

        // ✅ FIX: generateToken needs TWO args (email, companyId)
        String token = jwtUtil.generateToken(user.getEmail(), company.getId());
        log.info("Login: {}", user.getEmail());
        return buildAuthResponse(token, company.getId(), user);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private AuthResponse buildAuthResponse(String token, Long companyId, User user) {
        return new AuthResponse(
                token,
                companyId,
                user.getEmail(),
                user.getEffectiveTier(),
                user.trialDaysRemaining(),
                user.getAiChatsRemainingToday()
        );
    }
}