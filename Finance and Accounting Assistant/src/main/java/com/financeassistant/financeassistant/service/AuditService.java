package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/AuditService.java

import com.financeassistant.financeassistant.entity.AuditLog;
import com.financeassistant.financeassistant.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;

    /**
     * Log an action asynchronously — never blocks the caller.
     */
    @Async
    public void log(Long userId, Long companyId, String action,
                    String entityType, Long entityId,
                    String oldValue, String newValue, String ipAddress) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setCompanyId(companyId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setOldValue(oldValue);
            entry.setNewValue(newValue);
            entry.setIpAddress(ipAddress);
            auditRepo.save(entry);
        } catch (Exception e) {
            log.error("Audit log write failed: {}", e.getMessage());
        }
    }

    public Page<AuditLog> getAuditLog(Long companyId, int page, int size) {
        return auditRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(page, size));
    }

    // Convenience constants for action names
    public static final String CREATE_TRANSACTION  = "CREATE_TRANSACTION";
    public static final String DELETE_TRANSACTION  = "DELETE_TRANSACTION";
    public static final String UPDATE_TRANSACTION  = "UPDATE_TRANSACTION";
    public static final String EXPORT_PDF          = "EXPORT_PDF";
    public static final String EXPORT_CSV          = "EXPORT_CSV";
    public static final String INVITE_MEMBER       = "INVITE_MEMBER";
    public static final String REMOVE_MEMBER       = "REMOVE_MEMBER";
    public static final String SUBSCRIPTION_CHANGE = "SUBSCRIPTION_CHANGE";
}
