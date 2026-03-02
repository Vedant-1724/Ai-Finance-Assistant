package com.financeassistant.financeassistant.entity;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/entity/Budget.java

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;           // null = "Overall" budget

    @Column(nullable = false)
    private LocalDate month;             // always first day of month e.g. 2026-03-01

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        // Normalise month to first day
        if (this.month != null) {
            this.month = this.month.withDayOfMonth(1);
        }
    }
}
