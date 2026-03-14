package com.company.company_clean_hub_be.service;

import java.util.List;
import java.util.Optional;

import com.company.company_clean_hub_be.dto.request.VerificationCaptureRequest;
import com.company.company_clean_hub_be.dto.request.VerificationApprovalRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentVerificationResponse;
import com.company.company_clean_hub_be.dto.response.VerificationImageResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentVerification;

public interface VerificationService {

    // Core verification management
    AssignmentVerification createVerificationRequirement(Assignment assignment, String reason);

    Optional<AssignmentVerificationResponse> getVerificationByAssignmentId(Long assignmentId);

    List<AssignmentVerificationResponse> getPendingVerifications();

    // Image capture
    VerificationImageResponse captureVerificationImage(VerificationCaptureRequest request);

    List<VerificationImageResponse> getVerificationImages(Long verificationId);

    List<VerificationImageResponse> getImagesByAttendanceId(Long attendanceId);

    // Approval workflow
    AssignmentVerificationResponse approveVerification(VerificationApprovalRequest request, String approverUsername);

    AssignmentVerificationResponse rejectVerification(Long verificationId, String reason, String approverUsername);

    // Helper methods
    boolean requiresVerification(Assignment assignment);

    boolean isEmployeeNew(Long employeeId);

    boolean canCaptureImage(Long verificationId);
    
    // Auto-approval
    void processAutoApprovals();
}