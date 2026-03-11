package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.BulkImportRequest;
import com.financeassistant.financeassistant.dto.ImportTransactionsResultDto;
import com.financeassistant.financeassistant.service.StatementImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/transactions/import")
@RequiredArgsConstructor
public class StatementImportController {

    private final StatementImportService importService;

    @PostMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<ImportTransactionsResultDto> importStatementTransactions(
            @PathVariable Long companyId,
            @Valid @RequestBody BulkImportRequest request) {

        log.info("Statement import: company={}, count={}", companyId, request.getTransactions().size());
        ImportTransactionsResultDto result = importService.importTransactions(companyId, request);
        HttpStatus status = result.imported() > 0 ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
