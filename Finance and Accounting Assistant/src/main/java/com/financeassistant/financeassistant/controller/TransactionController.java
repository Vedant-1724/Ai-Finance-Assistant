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
 * PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *       financeassistant/controller/TransactionController.java
 *
 * CRITICAL FIX: @PreAuthorize added to ALL endpoints.
 * Before this fix, any authenticated user (User A) could read or delete
 * any other user's (User B's) transactions just by changing the companyId
 * in the URL — a textbook IDOR (Insecure Direct Object Reference) vulnerability.
 *
 * @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
 * delegates to CompanySecurityService.isOwner() which queries the DB to
 * confirm the authenticated user actually owns this companyId.
 * If the check fails, Spring Security throws 403 Forbidden before the
 * service method is ever called.
 *
 * Additional security: amount is server-validated (positive = income, negative = expense).
 * Max amount cap (₹10 crore) prevents manipulation of financial totals.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    /**
     * GET /api/v1/{companyId}/transactions
     * Returns all transactions for this company, newest first.
     * REQUIRES: authenticated user owns companyId.
     */
    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<TransactionDTO>> list(@PathVariable Long companyId) {
        log.info("GET /transactions companyId={}", companyId);
        return ResponseEntity.ok(service.getTransactions(companyId));
    }

    /**
     * POST /api/v1/{companyId}/transactions
     * Creates a new transaction. Amount sign convention:
     *   positive (+) = income
     *   negative (-) = expense
     * REQUIRES: authenticated user owns companyId.
     */
    @PostMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<TransactionDTO> add(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateTransactionRequest req) {
        log.info("POST /transactions companyId={} amount={}", companyId, req.getAmount());
        TransactionDTO created = service.createTransaction(companyId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * DELETE /api/v1/{companyId}/transactions/{transactionId}
     * Hard-deletes a transaction. TransactionService also verifies company
     * ownership at the DB level (defense-in-depth).
     * REQUIRES: authenticated user owns companyId.
     */
    @DeleteMapping("/{transactionId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<Void> delete(
            @PathVariable Long companyId,
            @PathVariable Long transactionId) {
        log.info("DELETE /transactions/{} companyId={}", transactionId, companyId);
        service.deleteTransaction(companyId, transactionId);
        return ResponseEntity.noContent().build();
    }
}
