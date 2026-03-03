package com.financeassistant.financeassistant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String source;

    @Column(length = 100)
    private String referenceNumber;

    @Column(name = "is_recurring")
    private boolean recurring;

    @Column(name = "recurring_interval")
    private String recurringInterval;

    @Column(name = "ai_categorized")
    private boolean aiCategorized;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "is_anomaly")
    private boolean anomaly;

    @Column(name = "anomaly_reason", length = 255)
    private String anomalyReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "recurrence_interval", length = 50)
    private String recurrenceInterval;

    @Column(name = "recurrence_end_date")
    private LocalDate recurrenceEndDate;

    @Column(name = "parent_transaction_id")
    private Long parentTransactionId;

    @Column(name = "account", length = 100)
    private String account;

    public enum TransactionType {
        INCOME, EXPENSE
    }
}