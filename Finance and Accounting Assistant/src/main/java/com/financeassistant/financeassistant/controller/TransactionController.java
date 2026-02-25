package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    /**
     * GET /api/v1/{companyId}/transactions
     * Returns all transactions for the company, newest first.
     */
    @GetMapping
    public ResponseEntity<List<TransactionDTO>> list(
            @PathVariable Long companyId) {

        log.info("GET /transactions companyId={}", companyId);
        List<TransactionDTO> dtos = service.getTransactions(companyId);
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/v1/{companyId}/transactions
     * Body: { "date": "2026-02-25", "amount": 50000, "description": "Client Payment" }
     */
    @PostMapping
    public ResponseEntity<TransactionDTO> add(
            @PathVariable Long companyId,
            @RequestBody CreateTransactionRequest req) {

        log.info("POST /transactions companyId={} amount={}", companyId, req.getAmount());
        TransactionDTO created = service.createTransaction(companyId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}