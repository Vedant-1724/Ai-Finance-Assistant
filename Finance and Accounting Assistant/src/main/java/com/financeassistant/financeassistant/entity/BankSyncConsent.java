package com.financeassistant.financeassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_sync_consents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankSyncConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private Company company;

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "state_token", nullable = false, unique = true, length = 120)
    private String stateToken;

    @Column(name = "provider_consent_id", length = 255)
    private String providerConsentId;

    @Column(name = "consent_url", columnDefinition = "TEXT")
    private String consentUrl;

    @Column(name = "mock_fallback", nullable = false)
    private boolean mockFallback;

    @Column(name = "consent_expires_at")
    private LocalDateTime consentExpiresAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        CONSENT_REQUIRED,
        CONSENT_GRANTED,
        SYNCED,
        FAILED,
        EXPIRED
    }

    public boolean isExpired() {
        return consentExpiresAt != null && consentExpiresAt.isBefore(LocalDateTime.now());
    }
}
