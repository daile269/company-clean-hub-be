package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSummaryDTO {
    private Long payrollId;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private Integer month;
    private Integer year;
    private LocalDateTime updatedAt;
    private BigDecimal advanceNote;
    private BigDecimal totalSalary;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
}
