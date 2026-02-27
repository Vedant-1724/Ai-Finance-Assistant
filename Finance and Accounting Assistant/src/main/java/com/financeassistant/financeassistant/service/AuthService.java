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

/**
 * Handles user registration and login.
 * Also implements UserDetailsService so Spring Security / JwtAuthFilter
 * can load users by email during JWT validation.
 *
 * ── Why AuthenticationManager was removed ────────────────────────────────────
 * Injecting AuthenticationManager here creates an unresolvable cycle:
 *
 *   AuthService  →  AuthenticationManager
 *                       ↓
 *              (Spring Security builds it by scanning
 *               for the UserDetailsService bean)
 *                       ↓
 *                   AuthService   ← cycle!
 *
 * The fix: verify the password directly with PasswordEncoder.
 * This is exactly what AuthenticationManager (DaoAuthenticationProvider)
 * does internally — we just skip the middleman.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository    userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtUtil           jwtUtil;

    // ── UserDetailsService ────────────────────────────────────────────────────

    /**
     * Called by JwtAuthFilter to load the user entity by email.
     * Spring Security uses the returned UserDetails to verify the JWT.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + email));
    }

    // ── Register ──────────────────────────────────────────────────────────────

    /**
     * Creates a new user + company, then returns a signed JWT.
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }

        User user = new User(
                req.getEmail(),
                passwordEncoder.encode(req.getPassword()),
                "USER"
        );
        User savedUser = userRepository.save(user);
        log.info("Registered new user: {}", savedUser.getEmail());

        Company company = new Company();
        company.setOwnerId(savedUser.getId());
        company.setName(req.getCompanyName());
        company.setCurrency("USD");
        Company savedCompany = companyRepository.save(company);
        log.info("Created company '{}' (id={}) for user {}",
                savedCompany.getName(), savedCompany.getId(), savedUser.getEmail());

        String token = jwtUtil.generateToken(savedUser.getEmail(), savedCompany.getId());
        return new AuthResponse(token, savedCompany.getId(), savedUser.getEmail());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Verifies email + password directly with BCrypt, then returns a signed JWT.
     *
     * We call passwordEncoder.matches() ourselves instead of delegating to
     * AuthenticationManager — identical behaviour, zero circular dependency.
     */
    public AuthResponse login(LoginRequest req) {
        // 1. Load user — throws UsernameNotFoundException if not found
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        // 2. Verify BCrypt hash — throws BadCredentialsException on mismatch
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3. Load the user's primary company
        Company company = companyRepository.findFirstByOwnerId(user.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No company found for user: " + req.getEmail()));

        log.info("User {} logged in, companyId={}", user.getEmail(), company.getId());

        String token = jwtUtil.generateToken(user.getEmail(), company.getId());
        return new AuthResponse(token, company.getId(), user.getEmail());
    }
}