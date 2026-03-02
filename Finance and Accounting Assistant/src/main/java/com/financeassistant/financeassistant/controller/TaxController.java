package com.financeassistant.financeassistant.controller;
// PATH: TaxController.java
import com.financeassistant.financeassistant.service.TaxService;
import com.financeassistant.financeassistant.service.TaxService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/v1/{companyId}/tax") @RequiredArgsConstructor
public class TaxController {
    private final TaxService taxService;
    @GetMapping("/gst")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<GstSummary> gst(@PathVariable Long companyId,
            @RequestParam(defaultValue="0") int year,
            @RequestParam(defaultValue="0") int quarter) {
        int y = year==0 ? LocalDate.now().getYear() : year;
        int q = quarter==0 ? ((LocalDate.now().getMonthValue()-1)/3)+1 : quarter;
        return ResponseEntity.ok(taxService.computeGstSummary(companyId, y, q));
    }
    @GetMapping("/income")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<IncomeTaxEstimate> income(@PathVariable Long companyId,
            @RequestParam(defaultValue="0") int year) {
        // Indian FY: April-March. If month < April, FY year = current year - 1
        int y = year==0 ? (LocalDate.now().getMonthValue() >= 4 ?
                LocalDate.now().getYear() : LocalDate.now().getYear()-1) : year;
        return ResponseEntity.ok(taxService.estimateIncomeTax(companyId, y));
    }
}
