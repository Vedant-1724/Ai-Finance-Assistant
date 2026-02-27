package com.financeassistant.financeassistant.config;

import com.financeassistant.financeassistant.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * Public endpoints  → /api/v1/auth/** (login & register)
 * Protected         → everything else under /api/v1/**
 *
 * PasswordEncoder and AuthenticationManager are in AppConfig to
 * avoid the circular dependency:
 *   CorsConfig → JwtAuthFilter → AuthService → PasswordEncoder → CorsConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class CorsConfig {

    // Injected via field — using @Autowired here avoids constructor injection
    // which would create a bean cycle with JwtAuthFilter → AuthService → PasswordEncoder
    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ── Disable CSRF — not needed for stateless REST ──────────────
                .csrf(AbstractHttpConfigurer::disable)

                // ── Stateless sessions — no HTTP session created ──────────────
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Disable HTTP Basic + form login ───────────────────────────
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // ── CORS — allow React dev server ─────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── Endpoint authorisation ────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints are public (login & register)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Actuator health endpoint — useful for Docker/k8s probes
                        .requestMatchers("/actuator/health").permitAll()
                        // All other API requests require a valid JWT
                        .anyRequest().authenticated()
                )

                // ── JWT filter runs before Spring's username/password filter ──
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration — allows requests from the React Vite dev server.
     * Add your production domain here when deploying.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
