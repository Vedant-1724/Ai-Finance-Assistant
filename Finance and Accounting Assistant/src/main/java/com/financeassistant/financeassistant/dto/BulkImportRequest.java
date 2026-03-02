// FIX: Added @DecimalMin / @DecimalMax on amount so Spring's @Valid
//      catches bad amounts at the HTTP boundary, not just in the service.

package com.financeassistant.financeassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        @PastOrPresent(message = "Transaction date cannot be in the future")
        private LocalDate date;

        @NotNull(message = "Description is required")
        @NotBlank(message = "Description cannot be blank")
        @Size(max = 512, message = "Description too long")
        private String description;

        /**
         * Positive = income, Negative = expense.
         * FIX: @DecimalMin/@DecimalMax added so HTTP layer validates this,
         *      not just the service layer.
         */
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "-10000000.00", message = "Amount below minimum (-₹1 crore)")
        @DecimalMax(value = "10000000.00",  message = "Amount exceeds maximum (₹1 crore)")
        private BigDecimal amount;

        /**
         * Source of the transaction: CSV_IMPORT, PDF, UPI_SCREENSHOT, etc.
         * Stored for audit trail only.
         */
        @Size(max = 50)
        @Pattern(
                regexp = "^(CSV_IMPORT|PDF|UPI_SCREENSHOT|IMAGE|TEXT|IMPORT)?$",
                message = "Invalid source value"
        )
        private String source;
    }
}