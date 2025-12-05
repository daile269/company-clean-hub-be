package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    
    private Integer month;
    private Integer year;
    private Integer totalDays;
    private BigDecimal salaryBase;
    private BigDecimal bonusTotal;
    private BigDecimal penaltyTotal;
    private BigDecimal advanceTotal;
    private BigDecimal allowanceTotal;
    private BigDecimal insuranceTotal;
    private BigDecimal finalSalary;
    
    private Boolean isPaid;
    private LocalDateTime paymentDate;
    
    private Long accountantId;
    private String accountantName;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
