package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Full Transaction CRUD with audit logging and company ownership checks.
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
            @Valid @RequestBody CreateTransactionRequest req,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpReq) {
        log.info("POST /transactions companyId={} amount={}", companyId, req.getAmount());
        TransactionDTO created = service.createTransaction(companyId, req,
                user, httpReq.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{transactionId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<TransactionDTO> update(
            @PathVariable Long companyId,
            @PathVariable Long transactionId,
            @Valid @RequestBody CreateTransactionRequest req,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpReq) {
        log.info("PUT /transactions/{} companyId={}", transactionId, companyId);
        TransactionDTO updated = service.updateTransaction(companyId, transactionId, req,
                user, httpReq.getRemoteAddr());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{transactionId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<Void> delete(
            @PathVariable Long companyId,
            @PathVariable Long transactionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpReq) {
        log.info("DELETE /transactions/{} companyId={}", transactionId, companyId);
        service.deleteTransaction(companyId, transactionId,
                user, httpReq.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
