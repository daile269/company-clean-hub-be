package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotalDaysResponse {
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private Integer month;
    private Integer year;
    private Integer totalDays;
    private String message;
}
