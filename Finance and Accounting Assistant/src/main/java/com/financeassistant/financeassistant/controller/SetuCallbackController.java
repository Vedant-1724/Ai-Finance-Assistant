package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.BankSyncStatusDto;
import com.financeassistant.financeassistant.service.SetuBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setu")
@RequiredArgsConstructor
public class SetuCallbackController {

    private final SetuBankService setuBankService;

    @GetMapping("/callback")
    public ResponseEntity<BankSyncStatusDto> handleCallback(
            @RequestParam("state") String state,
            @RequestParam(value = "consentId", required = false) String consentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "error", required = false) String error) {
        return ResponseEntity.ok(setuBankService.handleCallback(state, consentId, status, error));
    }
}
