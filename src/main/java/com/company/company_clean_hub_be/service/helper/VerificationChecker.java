package com.company.company_clean_hub_be.service.helper;

import org.springframework.stereotype.Component;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.WorkScheduleReason;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AssignmentVerificationRepository;
import com.company.company_clean_hub_be.repository.WorkScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to check verification requirements
 * Breaks circular dependency between VerificationService and WorkScheduleService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationChecker {

    private final WorkScheduleRepository workScheduleRepository;
    private final AssignmentVerificationRepository verificationRepository;
    private final AssignmentRepository assignmentRepository;

    /**
     * Check if assignment requires verification
     * Same logic as VerificationService.requiresVerification() but without circular dependency
     */
    public boolean requiresVerification(Assignment assignment) {
        
        // Condition 0: Check if employee has started but NOT completed 5 NEW_EMPLOYEE_VERIFICATION photos
        Long verifiedNewEmployeeCount = workScheduleRepository.countVerifiedSchedulesByEmployeeAndReason(
                assignment.getEmployee().getId(), WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION);
        if (verifiedNewEmployeeCount > 0 && verifiedNewEmployeeCount < 5) {
            log.info("Assignment {} requires verification: employee {} has {} verified NEW_EMPLOYEE_VERIFICATION (< 5), needs to continue",
                    assignment.getId(), assignment.getEmployee().getId(), verifiedNewEmployeeCount);
            return true;
        }

        // Check approved verification — if employee already has an approved verification
        // This includes: APPROVED, AUTO_APPROVED, BYPASS_APPROVED
        Long completedCount = verificationRepository.countCompletedVerificationsByEmployee(
                assignment.getEmployee().getId());
        
        // If employee has completed verification AND has 5+ photos, no new verification needed
        if (completedCount > 0 && verifiedNewEmployeeCount >= 5) {
            log.info("Assignment {} does NOT require verification: employee {} has {} approved verification(s) and {} verified photos (>= 5)",
                    assignment.getId(), assignment.getEmployee().getId(), completedCount, verifiedNewEmployeeCount);
            return false;
        }

        // If employee has completed verification but 0 photos (bypass approved without photos),
        // still treat as completed - no new verification needed
        if (completedCount > 0) {
            log.info("Assignment {} does NOT require verification: employee {} has {} approved verification(s)",
                    assignment.getId(), assignment.getEmployee().getId(), completedCount);
            return false;
        }

        // Condition 1: Completely new employee (never had any OTHER assignment)
        if (isEmployeeCompletelyNew(assignment.getEmployee().getId(), assignment.getId())) {
            log.info("Assignment {} requires verification: NEW_EMPLOYEE", assignment.getId());
            return true;
        }

        // Condition 2: Contract setting
        if (assignment.getContract() != null && 
            Boolean.TRUE.equals(assignment.getContract().getRequiresImageVerification())) {
            log.info("Assignment {} requires verification: CONTRACT_REQUIREMENT", assignment.getId());
            return true;
        }

        return false;
    }

    private boolean isEmployeeCompletelyNew(Long employeeId, Long excludeAssignmentId) {
        Long totalAssignments = excludeAssignmentId != null
                ? assignmentRepository.countAssignmentsByEmployeeExcluding(employeeId, excludeAssignmentId)
                : assignmentRepository.countAssignmentsByEmployee(employeeId);
        return totalAssignments == 0;
    }
}
