package com.company.company_clean_hub_be.dto.response;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PayRollAssignmentExportExcel {
    // Employee info (will be merged across assignments)
    private Long employeeId;
    private String employeeName;
    private String bankName;
    private String bankAccount;
    private String phone;
    
    // Assignment-specific info
    private String assignmentType; // Mức lương (mapped to Vietnamese)
    private String projectCompany; // Công trình
    private BigDecimal baseSalary;
    // Assignment-level totals
    private Integer assignmentDays;
    private BigDecimal assignmentBonus;
    private BigDecimal assignmentPenalty;
    private BigDecimal assignmentAllowance;
    private BigDecimal assignmentInsurance;
    private BigDecimal assignmentAdvance;
    
    // Employee-level totals (for the total row)
    private Integer totalDays;
    private BigDecimal totalBonus;
    private BigDecimal totalPenalty;
    private BigDecimal totalAllowance;
    private BigDecimal totalInsurance;
    private BigDecimal totalAdvance;
    private BigDecimal finalSalary;
    
    // Flag to indicate if this is a total row
    private Boolean isTotalRow;
}

