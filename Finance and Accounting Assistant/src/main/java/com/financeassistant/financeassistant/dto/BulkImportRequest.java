package com.financeassistant.financeassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request body for bulk-importing transactions parsed from bank statements.
 * Each item contains ONLY analysis-safe data — no account numbers, IFSC,
 * card numbers, or mobile numbers (those are redacted by the Python parser).
 */
@Data
public class BulkImportRequest {

    @NotNull
    @NotEmpty
    @Valid
    @Size(max = 500, message = "Cannot import more than 500 transactions at once")
    private List<ImportItem> transactions;

    @Data
    public static class ImportItem {

        @NotNull(message = "Date is required")
        private LocalDate date;

        @NotNull(message = "Description is required")
        @Size(max = 512, message = "Description too long")
        private String description;

        /**
         * Positive = income, Negative = expense.
         * Max cap prevents financial manipulation.
         */
        @NotNull(message = "Amount is required")
        private BigDecimal amount;

        /**
         * Where this transaction came from: CSV_IMPORT, PDF, UPI_SCREENSHOT, etc.
         * Stored for audit trail — never contains sensitive info.
         */
        @Size(max = 50)
        private String source;
    }
}