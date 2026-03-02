package com.financeassistant.financeassistant.entity;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/entity/AuditLog.java

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "audit_logs",
       indexes = @Index(name = "idx_audit_company_date", columnList = "company_id, created_at"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String action;          // e.g. CREATE_TRANSACTION

    @Column(name = "entity_type", length = 50)
    private String entityType;      // e.g. Transaction

    @Column(name = "entity_id")
    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String oldValue;        // JSON string

    @Column(columnDefinition = "TEXT")
    private String newValue;        // JSON string

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
