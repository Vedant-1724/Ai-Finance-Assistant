package com.financeassistant.financeassistant.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedStatement {

    private List<ParsedTransaction> transactions;
    private String bankName;
    private String accountNumber;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedTransaction {
        private LocalDate date;
        private BigDecimal amount;
        private String description;
        private String source;
        private String type; // CREDIT or DEBIT
    }
}