package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeScheduleSummary {
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private Long totalSchedules;
    private Long verifiedCount;
    private Long missedCount;
    private Long scheduledCount;
}
