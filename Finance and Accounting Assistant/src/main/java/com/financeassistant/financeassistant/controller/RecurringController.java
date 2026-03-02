package com.financeassistant.financeassistant.controller;
// PATH: RecurringController.java
import com.financeassistant.financeassistant.service.RecurringTransactionService;
import com.financeassistant.financeassistant.service.RecurringTransactionService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/{companyId}/recurring") @RequiredArgsConstructor
public class RecurringController {
    private final RecurringTransactionService recurringService;
    @GetMapping("/upcoming")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<UpcomingRecurring>> upcoming(
            @PathVariable Long companyId,
            @RequestParam(defaultValue="5") int limit) {
        return ResponseEntity.ok(recurringService.getUpcoming(companyId, limit));
    }
}
