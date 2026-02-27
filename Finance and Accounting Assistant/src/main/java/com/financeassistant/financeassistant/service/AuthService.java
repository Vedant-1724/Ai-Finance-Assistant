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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login.
 * Also implements UserDetailsService so Spring Security can load
 * users by email during JWT validation in JwtAuthFilter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository        userRepository;
    private final CompanyRepository     companyRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;
    private final AuthenticationManager authenticationManager;

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
     * Creates a new user account and a default company, then returns a JWT.
     *
     * Steps:
     *   1. Validate email not already taken
     *   2. Hash password with BCrypt
     *   3. Save User
     *   4. Create a Company with this user as owner
     *   5. Generate and return JWT containing email + companyId
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }

        // Save user with BCrypt-hashed password
        User user = new User(
                req.getEmail(),
                passwordEncoder.encode(req.getPassword()),
                "USER"
        );
        User savedUser = userRepository.save(user);
        log.info("Registered new user: {}", savedUser.getEmail());

        // Create a default company for this user
        Company company = new Company();
        company.setOwnerId(savedUser.getId());
        company.setName(req.getCompanyName());
        company.setCurrency("USD");
        Company savedCompany = companyRepository.save(company);
        log.info("Created company '{}' (id={}) for user {}", 
                savedCompany.getName(), savedCompany.getId(), savedUser.getEmail());

        // Issue JWT
        String token = jwtUtil.generateToken(savedUser.getEmail(), savedCompany.getId());
        return new AuthResponse(token, savedCompany.getId(), savedUser.getEmail());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates email + password, then returns a JWT.
     *
     * Steps:
     *   1. AuthenticationManager verifies credentials (calls loadUserByUsername)
     *   2. Load user's primary company to get companyId
     *   3. Generate and return JWT containing email + companyId
     *
     * Throws AuthenticationException on bad credentials — Spring Security
     * converts this to a 401 response automatically.
     */
    public AuthResponse login(LoginRequest req) {
        // Let Spring Security verify credentials (throws on failure)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );

        // Load user (already verified — this should not fail)
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found after auth"));

        // Find the user's primary company
        Company company = companyRepository.findFirstByOwnerId(user.getId())
                .orElseThrow(() ->
                        new IllegalStateException("No company found for user: " + req.getEmail()));

        log.info("User {} logged in, company={}", user.getEmail(), company.getId());

        String token = jwtUtil.generateToken(user.getEmail(), company.getId());
        return new AuthResponse(token, company.getId(), user.getEmail());
    }
}
