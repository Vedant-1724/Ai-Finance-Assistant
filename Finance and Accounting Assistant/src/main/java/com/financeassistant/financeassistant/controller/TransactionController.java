package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.CategorySuggestionDto;
import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.CategoryService;
import com.financeassistant.financeassistant.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;
    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
    public ResponseEntity<List<TransactionDTO>> list(@PathVariable Long companyId) {
        log.info("GET /transactions companyId={}", companyId);
        return ResponseEntity.ok(service.getTransactions(companyId));
    }

    @PostMapping
    @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
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
    @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
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
    @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
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

    @PostMapping("/categorize")
    @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
    public ResponseEntity<CategorySuggestionDto> categorize(
            @PathVariable Long companyId,
            @RequestBody Map<String, String> request) {
        String description = request.getOrDefault("description", "");
        String type = request.getOrDefault("type", "expense");
        return ResponseEntity.ok(categoryService.suggestCategory(companyId, description, type));
    }
}
