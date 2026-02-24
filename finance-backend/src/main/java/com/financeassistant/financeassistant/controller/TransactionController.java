package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.service.TransactionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/{companyId}/transactions")
public class TransactionController {

    @Autowired
    private TransactionService service;

    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<TransactionDTO>> list(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<TransactionDTO> dtos = service.findByCompany(
                companyId, PageRequest.of(page, size));
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<TransactionDTO> add(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateTransactionRequest req) {

        TransactionDTO created = service.create(companyId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}