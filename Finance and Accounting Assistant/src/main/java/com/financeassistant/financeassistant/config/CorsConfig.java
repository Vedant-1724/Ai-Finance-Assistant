// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/
//       financeassistant/config/CorsConfig.java
//
// FIX: addFilterAfter(subscriptionFilter, JwtAuthFilter.class) instead of
//      addFilterBefore with same anchor — guarantees JWT validates first,
//      THEN subscription is checked.

package com.financeassistant.financeassistant.config;

import com.financeassistant.financeassistant.security.JwtAuthFilter;
import com.financeassistant.financeassistant.security.SubscriptionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    private final JwtAuthFilter jwtAuthFilter;
    private final SubscriptionFilter subscriptionFilter;

    public CorsConfig(JwtAuthFilter jwtAuthFilter, SubscriptionFilter subscriptionFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.subscriptionFilter = subscriptionFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // ── Public endpoints ──────────────────────────────────────────
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register"
                        ).permitAll()

                        // Razorpay webhook — public, signature-verified inside controller
                        .requestMatchers("/api/v1/payment/webhook").permitAll()

                        // Health probes
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Everything else requires a valid JWT
                        .anyRequest().authenticated()
                )

                // ── FIXED: guaranteed order JwtAuthFilter → SubscriptionFilter ────
                // addFilterBefore puts JwtAuthFilter before UsernamePasswordAuth
                // addFilterAfter puts SubscriptionFilter AFTER JwtAuthFilter
                // This ensures JWT is validated before subscription is checked.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(subscriptionFilter, JwtAuthFilter.class)   // <── FIX

                // ── Security response headers ─────────────────────────────────────
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )
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
                        .referrerPolicy(ref ->
                                ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With",
                "X-Razorpay-Signature"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}