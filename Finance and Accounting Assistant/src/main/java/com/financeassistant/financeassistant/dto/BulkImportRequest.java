package com.financeassistant.financeassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
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
        @Size(max = 500, message = "Description too long")
        private String description;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "-10000000.00", message = "Amount below minimum (-₹1 crore)")
        @DecimalMax(value = "10000000.00", message = "Amount exceeds maximum (₹1 crore)")
        private BigDecimal amount;

        // Source is normalized server-side so parser display labels and legacy values
        // do not fail import after the user approves the preview.
        @Size(max = 50)
        private String source;
    }
}
