package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/TaxService.java

import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxService {

    private final TransactionRepository txnRepo;

    /**
     * Returns quarterly GST summary.
     * Quarter: 1=Jan-Mar, 2=Apr-Jun, 3=Jul-Sep, 4=Oct-Dec
     */
    public GstSummary computeGstSummary(Long companyId, int year, int quarter) {
        LocalDate[] range = getQuarterRange(year, quarter);
        List<Transaction> txns = txnRepo.findByCompanyIdOrderByDateDesc(companyId).stream()
                .filter(t -> !t.getDate().isBefore(range[0]) && !t.getDate().isAfter(range[1]))
                .toList();

        Map<Integer, GstSlab> slabs = new LinkedHashMap<>();
        for (int rate : new int[]{0, 5, 12, 18, 28}) {
            slabs.put(rate, new GstSlab(rate, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalGst     = BigDecimal.ZERO;

        for (Transaction tx : txns) {
            if (tx.getAmount().compareTo(BigDecimal.ZERO) < 0) continue; // skip expenses
            int rate = tx.getCategory() != null ? tx.getCategory().getGstRate() : 18;
            BigDecimal taxableAmt = tx.getAmount();
            BigDecimal gstAmt = taxableAmt.multiply(BigDecimal.valueOf(rate))
                    .divide(BigDecimal.valueOf(100 + rate), 4, RoundingMode.HALF_UP);

            GstSlab slab = slabs.get(rate);
            slabs.put(rate, new GstSlab(rate,
                    slab.taxableAmount().add(taxableAmt),
                    slab.cgst().add(gstAmt.divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP)),
                    slab.sgst().add(gstAmt.divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP))));

            totalTaxable = totalTaxable.add(taxableAmt);
            totalGst     = totalGst.add(gstAmt);
        }

        return new GstSummary(year, quarter, range[0], range[1],
                totalTaxable, totalGst,
                totalGst.divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP),
                totalGst.divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP),
                new ArrayList<>(slabs.values()));
    }

    /**
     * Estimate annual income tax under New Tax Regime FY2025-26.
     */
    public IncomeTaxEstimate estimateIncomeTax(Long companyId, int year) {
        LocalDate start = LocalDate.of(year, 4, 1);    // Indian FY April-March
        LocalDate end   = LocalDate.of(year + 1, 3, 31);

        BigDecimal income  = txnRepo.sumIncome(companyId, start, end);
        BigDecimal expense = txnRepo.sumExpense(companyId, start, end).abs();
        BigDecimal netProfit = income.subtract(expense);

        if (netProfit.compareTo(BigDecimal.ZERO) <= 0) {
            return new IncomeTaxEstimate(year, income, expense, netProfit, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    computeAdvanceTaxSchedule(BigDecimal.ZERO));
        }

        // New Tax Regime slabs FY2025-26 (no deductions)
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal p = netProfit;
        BigDecimal[] limits = {BigDecimal.valueOf(300000), BigDecimal.valueOf(700000),
                               BigDecimal.valueOf(1000000), BigDecimal.valueOf(1200000),
                               BigDecimal.valueOf(1500000)};
        int[]     rates  = {0, 5, 10, 15, 20, 30};

        BigDecimal prev = BigDecimal.ZERO;
        for (int i = 0; i < limits.length; i++) {
            BigDecimal slab = limits[i].subtract(prev);
            if (p.compareTo(prev) > 0) {
                BigDecimal taxable = p.subtract(prev).min(slab);
                tax = tax.add(taxable.multiply(BigDecimal.valueOf(rates[i]))
                              .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
            prev = limits[i];
        }
        if (p.compareTo(BigDecimal.valueOf(1500000)) > 0) {
            tax = tax.add(p.subtract(BigDecimal.valueOf(1500000))
                          .multiply(BigDecimal.valueOf(30))
                          .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }

        // Standard deduction ₹75,000 under new regime
        tax = tax.max(BigDecimal.ZERO);
        BigDecimal cess     = tax.multiply(BigDecimal.valueOf(4)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalTax = tax.add(cess);

        return new IncomeTaxEstimate(year, income, expense, netProfit, tax, cess, totalTax,
                totalTax.divide(netProfit, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)),
                computeAdvanceTaxSchedule(totalTax));
    }

    private List<AdvanceTaxInstalment> computeAdvanceTaxSchedule(BigDecimal totalTax) {
        // Advance tax instalments for individuals (BUSINESS income)
        return List.of(
                new AdvanceTaxInstalment("15 Jun", "15%", totalTax.multiply(BigDecimal.valueOf(0.15)).setScale(2, RoundingMode.HALF_UP)),
                new AdvanceTaxInstalment("15 Sep", "45%", totalTax.multiply(BigDecimal.valueOf(0.45)).setScale(2, RoundingMode.HALF_UP)),
                new AdvanceTaxInstalment("15 Dec", "75%", totalTax.multiply(BigDecimal.valueOf(0.75)).setScale(2, RoundingMode.HALF_UP)),
                new AdvanceTaxInstalment("15 Mar", "100%", totalTax)
        );
    }

    private LocalDate[] getQuarterRange(int year, int quarter) {
        return switch (quarter) {
            case 1 -> new LocalDate[]{LocalDate.of(year, 1, 1),  LocalDate.of(year, 3, 31)};
            case 2 -> new LocalDate[]{LocalDate.of(year, 4, 1),  LocalDate.of(year, 6, 30)};
            case 3 -> new LocalDate[]{LocalDate.of(year, 7, 1),  LocalDate.of(year, 9, 30)};
            case 4 -> new LocalDate[]{LocalDate.of(year, 10, 1), LocalDate.of(year, 12, 31)};
            default -> throw new IllegalArgumentException("Quarter must be 1-4");
        };
    }

    // ── Response records ──────────────────────────────────────────────────────
    public record GstSlab(int rate, BigDecimal taxableAmount, BigDecimal cgst, BigDecimal sgst) {}

    public record GstSummary(int year, int quarter, LocalDate startDate, LocalDate endDate,
                              BigDecimal totalTaxable, BigDecimal totalGst,
                              BigDecimal totalCgst, BigDecimal totalSgst,
                              List<GstSlab> slabs) {}

    public record AdvanceTaxInstalment(String dueDate, String cumulative, BigDecimal amount) {}

    public record IncomeTaxEstimate(int financialYear,
                                    BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal netProfit,
                                    BigDecimal incomeTax, BigDecimal cess, BigDecimal totalTax,
                                    BigDecimal effectiveRatePercent,
                                    List<AdvanceTaxInstalment> advanceTaxSchedule) {}
}
