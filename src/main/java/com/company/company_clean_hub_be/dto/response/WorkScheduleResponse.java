package com.company.company_clean_hub_be.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.company.company_clean_hub_be.entity.WorkScheduleReason;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleResponse {
    
    private Long id;
    private Long assignmentId;
    private Long employeeId;
    private String employeeName;
    private Long contractId;
    private LocalDate scheduledDate;
    private WorkScheduleStatus status;
    private String statusDescription;
    private WorkScheduleReason reason;
    private String reasonDescription;
    
    // Verification info
    private Long assignmentVerificationId;
    private Long verificationImageId;
    private Long attendanceId;
    
    // Photo tracking
    private LocalDateTime photoCapturedAt;
    private Boolean canCapturePhoto;
    
    // Sync info
    private Boolean attendanceDeleted;
    private String syncNote;
    private LocalDateTime lastSyncedAt;
    
    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
