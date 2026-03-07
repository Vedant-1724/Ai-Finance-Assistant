package com.financeassistant.financeassistant.entity;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/entity/FinancialHealthScore.java

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;

@Data
@NoArgsConstructor
@Entity
@Table(name = "financial_health_scores", uniqueConstraints = @UniqueConstraint(columnNames = { "company_id", "month" }))
public class FinancialHealthScore implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private LocalDate month; // first day of month

    @Column(nullable = false)
    private Integer score; // 0–100

    @Column(columnDefinition = "TEXT")
    private String breakdown; // JSON: {profitMargin:30, expenseGrowth:20, ...}

    @Column(columnDefinition = "TEXT")
    private String recommendations; // AI-generated text

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
