package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.BankSyncStatusDto;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.service.SetuBankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/setu")
@RequiredArgsConstructor
public class SetuBankController {

    private final SetuBankService setuBankService;

    @PostMapping("/consent")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<BankSyncStatusDto> createConsentUrl(@PathVariable Long companyId) {
        log.info("Generating Setu AA consent for companyId={}", companyId);
        return ResponseEntity.ok(setuBankService.createConsent(companyId));
    }

    @GetMapping("/status")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<BankSyncStatusDto> getStatus(@PathVariable Long companyId) {
        return ResponseEntity.ok(setuBankService.getSyncStatus(companyId));
    }

    @PostMapping("/sync")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<TransactionDTO>> syncBankData(@PathVariable Long companyId) {
        log.info("Triggering Setu AA sync for companyId={}", companyId);
        return ResponseEntity.ok(setuBankService.syncTransactions(companyId));
    }
}
