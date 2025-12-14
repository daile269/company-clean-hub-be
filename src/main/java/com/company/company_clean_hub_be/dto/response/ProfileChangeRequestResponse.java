package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.ProfileChangeRequest.ChangeType;
import com.company.company_clean_hub_be.entity.ProfileChangeRequest.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChangeRequestResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private Long requestedByUserId;
    private String requestedByUsername;
    private ChangeType changeType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String reason;
    private RequestStatus status;
    private Long approvedByUserId;
    private String approvedByUsername;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
