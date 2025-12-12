package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentPayrollDetailDto {
    private String salaryLevel; // Mức lương (từ AssignmentType)
    private Integer totalDays; // Tổng ngày
    private BigDecimal bonus; // Thưởng
    private BigDecimal penalty; // Phạt
    private BigDecimal allowance; // Phụ cấp
    private BigDecimal assignmentWorkAmount; // Thành tiền
}
