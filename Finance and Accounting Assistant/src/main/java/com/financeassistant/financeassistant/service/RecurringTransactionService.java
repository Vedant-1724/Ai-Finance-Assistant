package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/RecurringTransactionService.java

import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final TransactionRepository txnRepo;

    /**
     * Runs every day at midnight (00:01).
     * Finds all active recurring transactions due today and auto-creates the next occurrence.
     */
    @Scheduled(cron = "0 1 0 * * *")   // 00:01 daily
    @Transactional
    public void processRecurringTransactions() {
        LocalDate today = LocalDate.now();
        log.info("Processing recurring transactions for date={}", today);

        // Find all recurring parent transactions whose last occurrence was due today or earlier
        List<Transaction> allTxns = txnRepo.findAll(); // filtered below
        for (Transaction parent : allTxns) {
            if (!parent.isRecurring()) continue;
            if (parent.getParentTransactionId() != null) continue; // skip children
            if (parent.getRecurrenceInterval() == null) continue;
            if (parent.getRecurrenceEndDate() != null && today.isAfter(parent.getRecurrenceEndDate())) continue;

            // Find latest child of this series
            LocalDate lastDate = txnRepo.findByCompanyIdOrderByDateDesc(parent.getCompany().getId())
                    .stream()
                    .filter(t -> parent.getId().equals(t.getParentTransactionId()))
                    .map(Transaction::getDate)
                    .max(LocalDate::compareTo)
                    .orElse(parent.getDate());

            LocalDate nextDue = computeNextDate(lastDate, parent.getRecurrenceInterval());

            if (!nextDue.isAfter(today)) {
                createChild(parent, nextDue);
                log.info("Created recurring transaction for parent={} date={}", parent.getId(), nextDue);
            }
        }
    }

    private LocalDate computeNextDate(LocalDate last, String interval) {
        return switch (interval.toUpperCase()) {
            case "DAILY"   -> last.plusDays(1);
            case "WEEKLY"  -> last.plusWeeks(1);
            case "MONTHLY" -> last.plusMonths(1);
            case "YEARLY"  -> last.plusYears(1);
            default        -> last.plusMonths(1);
        };
    }

    @Transactional
    public void createChild(Transaction parent, LocalDate date) {
        Transaction child = new Transaction();
        child.setCompany(parent.getCompany());
        child.setAccount(parent.getAccount());
        child.setCategory(parent.getCategory());
        child.setDate(date);
        child.setAmount(parent.getAmount());
        child.setDescription(parent.getDescription() + " (Auto)");
        child.setSource("RECURRING");
        child.setRecurring(false);  // children are not themselves recurring
        child.setParentTransactionId(parent.getId());
        txnRepo.save(child);
    }

    /** Returns the next N upcoming recurring dates for all active series */
    public List<UpcomingRecurring> getUpcoming(Long companyId, int limit) {
        return txnRepo.findByCompanyIdOrderByDateDesc(companyId).stream()
                .filter(t -> t.isRecurring() && t.getParentTransactionId() == null)
                .filter(t -> t.getRecurrenceEndDate() == null || !t.getRecurrenceEndDate().isBefore(LocalDate.now()))
                .map(t -> {
                    LocalDate next = computeNextDate(t.getDate(), t.getRecurrenceInterval() != null ? t.getRecurrenceInterval() : "MONTHLY");
                    return new UpcomingRecurring(t.getId(), t.getDescription(), t.getAmount(), next, t.getRecurrenceInterval());
                })
                .sorted(java.util.Comparator.comparing(UpcomingRecurring::nextDate))
                .limit(limit)
                .toList();
    }

    public record UpcomingRecurring(Long transactionId, String description,
                                    java.math.BigDecimal amount, LocalDate nextDate,
                                    String interval) {}
}
