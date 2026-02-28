package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Transaction controller — hardened with ownership checks.
 *
 * FIXED: @PreAuthorize now ACTUALLY checks company ownership.
 * CompanySecurityService was "return true" before — now it verifies
 * the authenticated user owns the company. This prevents:
 *  - User A reading User B's transactions
 *  - Cross-tenant data leakage
 *  - Privilege escalation via URL manipulation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<TransactionDTO>> list(@PathVariable Long companyId) {
        log.info("GET /transactions companyId={}", companyId);
        return ResponseEntity.ok(service.getTransactions(companyId));
    }

    @PostMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<TransactionDTO> add(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateTransactionRequest req) {
        log.info("POST /transactions companyId={} amount={}", companyId, req.getAmount());
        TransactionDTO created = service.createTransaction(companyId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{transactionId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<?> delete(
            @PathVariable Long companyId,
            @PathVariable Long transactionId) {
        log.info("DELETE /transactions/{} companyId={}", transactionId, companyId);
        service.deleteTransaction(companyId, transactionId);
        return ResponseEntity.noContent().build();
    }
}