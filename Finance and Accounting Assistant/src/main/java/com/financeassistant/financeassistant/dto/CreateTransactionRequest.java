package com.financeassistant.financeassistant.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    private LocalDate  date;
    private BigDecimal amount;
    private String     description;
}
