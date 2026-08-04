package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentPayrollDetailResponse {
    private Long assignmentId;
    private BigDecimal baseSalary; // Lương cơ bản
    private Integer workDays; // Ngày công thực tế
    private BigDecimal monthlySupport; // Hỗ trợ tháng cộng thẳng, không chia ngày công
    private BigDecimal expectedSalary; // Lương dự kiến (đã gồm monthlySupport)
}
