package com.company.company_clean_hub_be.dto.request;

import com.company.company_clean_hub_be.entity.EvaluationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {
    @NotNull(message = "Attendance ID is required")
    private Long attendanceId;
    
    private Long employeeId;
    
    @NotNull(message = "Status is required")
    private EvaluationStatus status;
    
    private String internalNotes;
}
