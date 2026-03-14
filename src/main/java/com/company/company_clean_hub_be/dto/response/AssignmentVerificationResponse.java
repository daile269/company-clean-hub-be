package com.company.company_clean_hub_be.dto.response;

import java.time.LocalDateTime;

import com.company.company_clean_hub_be.entity.VerificationReason;
import com.company.company_clean_hub_be.entity.VerificationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentVerificationResponse {
    
    private Long id;
    private Long assignmentId;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private Long contractId;
    
    private VerificationReason reason;
    private VerificationStatus status;
    
    private Integer maxAttempts;
    private Integer currentAttempts;
    
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime autoApprovedAt;
    
    private Boolean isCompleted;
    private Boolean canCapture;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}