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
    private BigDecimal expectedSalary; // Lương dự kiến (từ công thức)
}
