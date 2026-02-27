package com.financeassistant.financeassistant.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Maps to the existing 'users' table created in V1__init_schema.sql.
 * Implements UserDetails so Spring Security can use it directly.
 *
 * Column mapping:
 *   id         → BIGSERIAL PRIMARY KEY
 *   email      → VARCHAR(255) UNIQUE NOT NULL
 *   password   → VARCHAR(255) NOT NULL  (widened in V3 migration to hold BCrypt hash)
 *   role       → VARCHAR(50)  NOT NULL  e.g. "ADMIN" | "USER"
 *   created_at → TIMESTAMP NOT NULL
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // Stores BCrypt hash after V3 migration
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String role = "USER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public User() {}

    public User(String email, String password, String role) {
        this.email    = email;
        this.password = password;
        this.role     = role;
    }

    // ── UserDetails interface ─────────────────────────────────────────────────

    /** Spring Security uses email as the username */
    @Override
    public String getUsername() {
        return email;
    }

    /** Returns BCrypt-hashed password — Spring Security verifies this */
    @Override
    public String getPassword() {
        return password;
    }

    /** Converts role string (e.g. "ADMIN") to GrantedAuthority */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getEmail()                   { return email; }
    public void setEmail(String email)         { this.email = email; }

    public void setPassword(String password)   { this.password = password; }

    public String getRole()                    { return role; }
    public void setRole(String role)           { this.role = role; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime t)  { this.createdAt = t; }
}
