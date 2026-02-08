package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollOverviewResponse {

    private Long totalPayrolls;
    private Long paidPayrolls;
    private Long unpaidPayrolls;
    private Long partialPaidPayrolls;

    private BigDecimal totalFinalSalary;
    private BigDecimal totalPaidAmount;
    private BigDecimal totalRemainingAmount;
}


