package com.company.company_clean_hub_be.dto.response;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PayRollExportExcel {
    private Long employeeId;
    private String employeeName;
    private String bankName;
    private String bankAccount;
    private String phone;

    private List<String> projectCompanies;

    private Integer totalDays;
    private BigDecimal totalBonus;
    private BigDecimal totalPenalty;
    private BigDecimal totalAllowance;
    private BigDecimal totalInsurance;
    private BigDecimal totalAdvance;
    private BigDecimal finalSalary;
}

