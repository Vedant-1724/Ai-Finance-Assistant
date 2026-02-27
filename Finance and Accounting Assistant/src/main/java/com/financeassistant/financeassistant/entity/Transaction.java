package com.financeassistant.financeassistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * FIX: Added Lombok @Data, @NoArgsConstructor, @AllArgsConstructor.
 * Added 'source' field (MANUAL/PLAID/CSV) and 'createdAt' with @PrePersist.
 * These match the DB schema (V1 migration has source + created_at columns).
 * Without Lombok annotations and the source field, Spring will fail to persist
 * or will miss columns defined in the migration SQL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions",
        indexes = {
                @Index(name = "idx_txn_company", columnList = "company_id"),
                @Index(name = "idx_txn_date",    columnList = "company_id, date")
        })
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false, length = 50)
    private String source = "MANUAL";   // MANUAL | PLAID | CSV

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.source == null) this.source = "MANUAL";
    }
}