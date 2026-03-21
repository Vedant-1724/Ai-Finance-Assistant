package com.financeassistant.financeassistant.controller;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/controller/ExportController.java

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.service.AuditService;
import com.financeassistant.financeassistant.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final AuditService auditService;
    private final CompanyRepository companyRepo;

    /**
     * GET /api/v1/{companyId}/export/pdf?period=month
     * Downloads a branded PDF report of all transactions.
     * Requires TRIAL or PRO subscription.
     */
    @GetMapping("/pdf")
    @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "all") String period,
            @AuthenticationPrincipal User user,
            HttpServletRequest req) throws IOException {

        String companyName = companyRepo.findById(companyId)
                .map(c -> c.getName()).orElse("My Company");

        byte[] pdf = exportService.exportPdf(companyId, companyName, period);

        auditService.log(user.getId(), companyId, AuditService.EXPORT_PDF,
                "Transaction", null, null, "period=" + period, req.getRemoteAddr());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transactions-" + LocalDate.now() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * GET /api/v1/{companyId}/export/csv
     * Downloads a CSV of all transactions. Available to all tiers.
     */
    @GetMapping("/csv")
    @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User user,
            HttpServletRequest req) throws IOException {

        String csv = exportService.exportCsv(companyId);

        auditService.log(user.getId(), companyId, AuditService.EXPORT_CSV,
                "Transaction", null, null, null, req.getRemoteAddr());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transactions-" + LocalDate.now() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }
}
