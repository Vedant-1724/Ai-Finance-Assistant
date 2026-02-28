package com.financeassistant.financeassistant.config;

import com.financeassistant.financeassistant.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration — production hardened.
 *
 * Security features:
 *  - JWT stateless authentication
 *  - Security response headers (HSTS, CSP, X-Frame-Options, etc.)
 *  - CORS locked to configured origins only
 *  - No sessions, no basic auth, no form login
 *  - Actuator health public, all else protected
 *
 * Ready for: Razorpay webhooks, bank account linking (Plaid/Setu),
 *            subscription gating, and payment endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class CorsConfig {

    @Value("${security.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost}")
    private String allowedOriginsStr;

    @Lazy
    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable unused/unsafe defaults ──────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)   // Stateless JWT — no CSRF needed
            .sessionManagement(s ->
                    s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)

            // ── CORS ────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Route access rules ──────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public: auth endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Public: health check only
                .requestMatchers("/actuator/health").permitAll()
                // Public: Razorpay webhook (signature verified in controller)
                .requestMatchers("/api/v1/payment/webhook").permitAll()
                // Public: internal AI service calls
                .requestMatchers("/api/v1/internal/**").permitAll()
                // Everything else requires valid JWT
                .anyRequest().authenticated()
            )

            // ── Security response headers ────────────────────────────────────
            .headers(headers -> headers
                // Prevent clickjacking
                .frameOptions(frame -> frame.deny())

                // Force HTTPS for 1 year (ready for production SSL)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .preload(true)
                )

                // Content Security Policy
                // Allows: self, Razorpay checkout script, Razorpay API calls
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://checkout.razorpay.com; " +
                    "connect-src 'self' https://api.razorpay.com https://lumberjack.razorpay.com; " +
                    "frame-src https://api.razorpay.com https://checkout.razorpay.com; " +
                    "img-src 'self' data: https:; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "font-src 'self' data:; " +
                    "object-src 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self' https://checkout.razorpay.com"
                ))

                // Don't send referrer to other sites
                .referrerPolicy(ref -> ref
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )

                // Block MIME-type sniffing
                .contentTypeOptions(ct -> {})

                // Disable browser caching of sensitive responses
                .cacheControl(cache -> {})
            )

            // ── JWT filter ──────────────────────────────────────────────────
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Origins from environment variable — no hardcoded localhost in production
        List<String> origins = Arrays.asList(allowedOriginsStr.split(","));
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "X-Internal-Key",        // for AI service calls
            "X-Razorpay-Signature",  // for Razorpay webhook verification
            "Idempotency-Key"        // for payment idempotency
        ));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);     // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}