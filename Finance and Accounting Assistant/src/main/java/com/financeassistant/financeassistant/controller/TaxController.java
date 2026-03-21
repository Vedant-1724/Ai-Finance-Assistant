package com.financeassistant.financeassistant.controller;

// PATH: TaxController.java
import com.financeassistant.financeassistant.service.TaxService;
import com.financeassistant.financeassistant.service.TaxService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/{companyId}/tax")
@RequiredArgsConstructor
public class TaxController {
        private final TaxService taxService;

        @GetMapping
        @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
        public ResponseEntity<TaxSummaryResponse> summary(@PathVariable Long companyId,
                        @RequestParam(defaultValue = "") String year,
                        @RequestParam(defaultValue = "") String quarter) {

                int parsedYear = 0;
                if (!year.isEmpty()) {
                        try {
                                parsedYear = Integer.parseInt(year.substring(0, 4));
                        } catch (Exception e) {
                        }
                }

                int parsedQuarter = 0;
                if (!quarter.isEmpty()) {
                        try {
                                parsedQuarter = Integer.parseInt(quarter.replace("Q", "").trim());
                        } catch (Exception e) {
                        }
                }

                int y = parsedYear == 0
                                ? (LocalDate.now().getMonthValue() >= 4 ? LocalDate.now().getYear()
                                                : LocalDate.now().getYear() - 1)
                                : parsedYear;
                int q = parsedQuarter == 0 ? ((LocalDate.now().getMonthValue() - 1) / 3) + 1 : parsedQuarter;

                GstSummary gst = taxService.computeGstSummary(companyId, y, q);
                IncomeTaxEstimate inc = taxService.estimateIncomeTax(companyId, y);

                List<GstEntry> breakdown = gst.slabs().stream()
                                .filter(s -> s.taxableAmount().compareTo(BigDecimal.ZERO) > 0)
                                .map(s -> new GstEntry("Sales @" + s.rate() + "%", s.rate(), s.taxableAmount(),
                                                s.cgst().add(s.sgst()),
                                                "INCOME"))
                                .toList();

                TaxSummaryResponse res = new TaxSummaryResponse(
                                y + "-" + (y + 1 - 2000), "Q" + q,
                                inc.totalIncome(), inc.totalExpense(), inc.netProfit(),
                                gst.totalGst(), BigDecimal.ZERO, gst.totalGst(),
                                inc.totalIncome().multiply(BigDecimal.valueOf(0.10)),
                                inc.totalTax(),
                                breakdown);

                return ResponseEntity.ok(res);
        }

        @GetMapping("/gst")
        @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
        public ResponseEntity<GstSummary> gst(@PathVariable Long companyId,
                        @RequestParam(defaultValue = "0") int year,
                        @RequestParam(defaultValue = "0") int quarter) {
                int y = year == 0 ? LocalDate.now().getYear() : year;
                int q = quarter == 0 ? ((LocalDate.now().getMonthValue() - 1) / 3) + 1 : quarter;
                return ResponseEntity.ok(taxService.computeGstSummary(companyId, y, q));
        }

        @GetMapping("/income")
        @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
        public ResponseEntity<IncomeTaxEstimate> income(@PathVariable Long companyId,
                        @RequestParam(defaultValue = "0") int year) {
                // Indian FY: April-March. If month < April, FY year = current year - 1
                int y = year == 0
                                ? (LocalDate.now().getMonthValue() >= 4 ? LocalDate.now().getYear()
                                                : LocalDate.now().getYear() - 1)
                                : year;
                return ResponseEntity.ok(taxService.estimateIncomeTax(companyId, y));
        }

        public record GstEntry(String categoryName, int gstRate, BigDecimal taxableAmount, BigDecimal gstAmount,
                        String type) {
        }

        public record TaxSummaryResponse(String financialYear, String quarter, BigDecimal totalIncome,
                        BigDecimal totalExpense, BigDecimal netProfit,
                        BigDecimal gstCollected, BigDecimal gstPaid, BigDecimal gstPayable, BigDecimal estimatedTDS,
                        BigDecimal estimatedAdvTax, List<GstEntry> breakdown) {
        }
}
