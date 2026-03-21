package com.financeassistant.financeassistant.controller;
// PATH: AuditController.java
import com.financeassistant.financeassistant.entity.AuditLog;
import com.financeassistant.financeassistant.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/{companyId}/audit") @RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;
    @GetMapping
    @PreAuthorize("@companySecurityService.isCompanyOwner(#companyId, authentication)")
    public ResponseEntity<Page<AuditLog>> getLog(
            @PathVariable Long companyId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(auditService.getAuditLog(companyId, page, Math.min(size, 100)));
    }
}
