package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.service.SetuBankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/setu")
@RequiredArgsConstructor
public class SetuBankController {

  private final SetuBankService setuBankService;

  /**
   * POST /api/v1/{companyId}/setu/consent
   * Generates a mock consent link for the user to "approve" data sharing.
   */
  @PostMapping("/consent")
  @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
  public ResponseEntity<Map<String, String>> createConsentUrl(@PathVariable Long companyId) {
    log.info("Generating Setu AA consent URL for companyId={}", companyId);
    String url = setuBankService.createConsentLink(companyId);
    return ResponseEntity.ok(Map.of("consentUrl", url));
  }

  /**
   * POST /api/v1/{companyId}/setu/sync
   * Simulates the callback/sync process after the user approves consent.
   */
  @PostMapping("/sync")
  @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
  public ResponseEntity<List<TransactionDTO>> syncBankData(@PathVariable Long companyId) {
    log.info("Triggering Setu AA sync for companyId={}", companyId);
    List<TransactionDTO> synced = setuBankService.syncTransactions(companyId);
    return ResponseEntity.ok(synced);
  }
}
