package com.company.company_clean_hub_be.dto.response;

import java.time.LocalDateTime;
import com.company.company_clean_hub_be.entity.EvaluationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {
    private Long id;
    private Long attendanceId;
    private Long employeeId;
    private String employeeName;
    private String evaluatedByUsername;
    private EvaluationStatus status;
    private String internalNotes;
    private LocalDateTime evaluatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
