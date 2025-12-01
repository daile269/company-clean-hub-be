package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemporaryAssignmentResponse {

    private List<AttendanceResponse> createdAttendances;
    private List<AttendanceResponse> deletedAttendances;
    private String message;
    private Integer replacementEmployeeTotalDays;
    private Integer replacedEmployeeTotalDays;
    private Integer processedDaysCount;
}
