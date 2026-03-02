package com.financeassistant.financeassistant.entity;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/entity/Transaction.java
// UPDATED: Added is_recurring, recurrence_interval, recurrence_end_date, parent_transaction_id

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private String source = "MANUAL";   // MANUAL | PLAID | CSV | BANK_SYNC

    // ── Recurring transaction fields (NEW) ───────────────────────────────────
    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "recurrence_interval", length = 20)
    private String recurrenceInterval;  // DAILY / WEEKLY / MONTHLY / YEARLY

    @Column(name = "recurrence_end_date")
    private LocalDate recurrenceEndDate;

    @Column(name = "parent_transaction_id")
    private Long parentTransactionId;   // links child to the original series

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.source == null) this.source = "MANUAL";
    }
}
