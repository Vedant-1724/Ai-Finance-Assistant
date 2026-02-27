package com.financeassistant.financeassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "anomalies",
        indexes = {
                @Index(name = "idx_anomaly_company",     columnList = "company_id"),
                @Index(name = "idx_anomaly_detected_at", columnList = "detected_at")
        }
)
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    // ── Constructors ──────────────────────────────────────────────────────────
    public Anomaly() {}

    public Anomaly(Long companyId, Long transactionId,
                   BigDecimal amount, LocalDateTime detectedAt) {
        this.companyId     = companyId;
        this.transactionId = transactionId;
        this.amount        = amount;
        this.detectedAt    = detectedAt;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public Long getCompanyId()                       { return companyId; }
    public void setCompanyId(Long companyId)         { this.companyId = companyId; }

    public Long getTransactionId()                   { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public BigDecimal getAmount()                    { return amount; }
    public void setAmount(BigDecimal amount)         { this.amount = amount; }

    public LocalDateTime getDetectedAt()                   { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt)    { this.detectedAt = detectedAt; }
}