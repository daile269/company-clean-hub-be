package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemporaryAssignmentResponse {

    private AttendanceResponse createdAttendance;
    private AttendanceResponse deletedAttendance;
    private String message;
    private Integer replacementEmployeeTotalDays;
    private Integer replacedEmployeeTotalDays;
}
