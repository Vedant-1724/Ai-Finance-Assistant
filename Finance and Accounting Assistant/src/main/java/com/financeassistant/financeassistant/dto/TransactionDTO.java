package com.financeassistant.financeassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data                   // generates getters + setters for ALL fields
@NoArgsConstructor      // generates default constructor (required by Jackson for JSON)
@AllArgsConstructor     // generates constructor with all fields
public class TransactionDTO {

    private Long       id;
    private String     date;
    private BigDecimal amount;
    private String     description;
    private String     categoryName;
}
