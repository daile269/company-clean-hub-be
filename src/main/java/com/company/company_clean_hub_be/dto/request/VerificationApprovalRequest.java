package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationApprovalRequest {
    
    @NotNull(message = "Verification ID is required")
    private Long verificationId;

    private String internalNotes; // Optional notes from approver
    
    private Boolean disableVerification; // Optional: disable verification for this assignment after approval
}