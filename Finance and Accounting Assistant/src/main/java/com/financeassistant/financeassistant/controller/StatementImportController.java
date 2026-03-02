package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.BulkImportRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.service.StatementImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoint for bulk-importing transactions that were parsed from
 * a bank statement by the Python AI service.
 *
 * FLOW:
 *  1. Frontend uploads file to Python /parse-statement
 *  2. Python redacts all sensitive fields and returns clean transactions
 *  3. User reviews and confirms the clean list in the UI
 *  4. Frontend POSTs confirmed transactions here
 *  5. This controller saves them (with server-side validation)
 *
 * SECURITY: @PreAuthorize ensures only the company owner can import.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/transactions/import")
@RequiredArgsConstructor
public class StatementImportController {

    private final StatementImportService importService;

    /**
     * POST /api/v1/{companyId}/transactions/import
     * Body: { transactions: [{date, description, amount, source}] }
     *
     * Returns: { imported: int, transactions: [...] }
     */
    @PostMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<Map<String, Object>> importStatementTransactions(
            @PathVariable Long companyId,
            @Valid @RequestBody BulkImportRequest request) {

        log.info("Statement import: company={}, count={}", companyId, request.getTransactions().size());

        List<TransactionDTO> saved = importService.importTransactions(companyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "imported",      saved.size(),
                "transactions",  saved,
                "message",       saved.size() + " transactions imported successfully"
        ));
    }
}