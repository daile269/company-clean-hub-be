package com.company.company_clean_hub_be.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.EvaluationRequest;
import com.company.company_clean_hub_be.dto.response.EvaluationResponse;
import com.company.company_clean_hub_be.dto.response.EvaluationDetailResponse;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Evaluation;
import com.company.company_clean_hub_be.entity.EvaluationStatus;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.entity.AssignmentVerification;
import com.company.company_clean_hub_be.entity.VerificationImage;
import com.company.company_clean_hub_be.exception.ResourceNotFoundException;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EvaluationRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.repository.VerificationImageRepository;
import com.company.company_clean_hub_be.service.EvaluationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final VerificationImageRepository verificationImageRepository;

    @Override
    @Transactional
    public EvaluationResponse evaluate(EvaluationRequest request, String evaluatorUsername) {
        Attendance attendance = attendanceRepository.findById(request.getAttendanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found with id: " + request.getAttendanceId()));

        User evaluator = userRepository.findByUsername(evaluatorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + evaluatorUsername));

        Evaluation evaluation = evaluationRepository.findByAttendanceId(request.getAttendanceId())
                .orElseGet(() -> Evaluation.builder()
                        .attendance(attendance)
                        .employee(attendance.getEmployee())
                        .build());

        evaluation.setStatus(request.getStatus());
        evaluation.setInternalNotes(request.getInternalNotes());
        evaluation.setEvaluatedBy(evaluator);
        
        if (request.getStatus() == EvaluationStatus.APPROVED) {
            evaluation.setEvaluatedAt(LocalDateTime.now());
        }

        Evaluation savedEvaluation = evaluationRepository.save(evaluation);

        return mapToResponse(savedEvaluation);
    }

    @Override
    public Optional<EvaluationResponse> getEvaluationByAttendanceId(Long attendanceId) {
        return evaluationRepository.findByAttendanceId(attendanceId)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationDetailResponse> getEvaluationDetails(Long evaluationId) {
        return evaluationRepository.findByIdWithAllRelations(evaluationId)
                .map(this::mapToDetailResponse);
    }

    private EvaluationResponse mapToResponse(Evaluation evaluation) {
        return EvaluationResponse.builder()
                .id(evaluation.getId())
                .attendanceId(evaluation.getAttendance().getId())
                .employeeId(evaluation.getEmployee().getId())
                .employeeName(evaluation.getEmployee().getName())
                .evaluatedByUsername(evaluation.getEvaluatedBy() != null ? evaluation.getEvaluatedBy().getUsername() : null)
                .status(evaluation.getStatus())
                .internalNotes(evaluation.getInternalNotes())
                .evaluatedAt(evaluation.getEvaluatedAt())
                .createdAt(evaluation.getCreatedAt())
                .updatedAt(evaluation.getUpdatedAt())
                .build();
    }

    private EvaluationDetailResponse mapToDetailResponse(Evaluation evaluation) {
        Attendance attendance = evaluation.getAttendance();
        Employee employee = evaluation.getEmployee();
        Assignment assignment = attendance.getAssignment();
        Contract contract = assignment != null ? assignment.getContract() : null;
        Customer customer = contract != null ? contract.getCustomer() : null;
        ServiceEntity service = null;
        if (contract != null && !contract.getServices().isEmpty()) {
            service = contract.getServices().iterator().next(); // Get first service
        }
        AssignmentVerification verification = attendance.getAssignmentVerification();
        User evaluatedBy = evaluation.getEvaluatedBy();
        User approvedBy = attendance.getApprovedBy();
        User assignedBy = assignment != null ? assignment.getAssignedBy() : null;

        // Get verification images if verification exists
        List<EvaluationDetailResponse.VerificationImageInfo> verificationImages = null;
        if (verification != null) {
            List<VerificationImage> images = verificationImageRepository.findByAssignmentVerificationId(verification.getId());
            verificationImages = images.stream()
                    .map(img -> EvaluationDetailResponse.VerificationImageInfo.builder()
                            .imageId(img.getId())
                            .imageUrl(img.getCloudinaryUrl())
                            .imageType("verification") // Default type since no imageType field
                            .uploadedAt(img.getCreatedAt().toString())
                            .build())
                    .collect(Collectors.toList());
        }

        return EvaluationDetailResponse.builder()
                // Evaluation info
                .evaluationId(evaluation.getId())
                .evaluationStatus(evaluation.getStatus())
                .internalNotes(evaluation.getInternalNotes())
                .evaluatedAt(evaluation.getEvaluatedAt())
                .evaluatedByUsername(evaluatedBy != null ? evaluatedBy.getUsername() : null)
                .evaluatedByName(evaluatedBy != null ? evaluatedBy.getUsername() : null) // Use username as name
                
                // Attendance info
                .attendanceId(attendance.getId())
                .attendanceDate(attendance.getDate())
                .bonus(attendance.getBonus())
                .penalty(attendance.getPenalty())
                .supportCost(attendance.getSupportCost())
                .workHours(attendance.getWorkHours())
                .isOvertime(attendance.getIsOvertime())
                .overtimeAmount(attendance.getOvertimeAmount())
                .attendanceDescription(attendance.getDescription())
                .approvedByUsername(approvedBy != null ? approvedBy.getUsername() : null)
                .approvedByName(approvedBy != null ? approvedBy.getUsername() : null) // Use username as name
                
                // Employee info
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .employeePhone(employee.getPhone())
                .employeeEmail(employee.getEmail())
                .employmentType(employee.getEmploymentType())
                .baseSalary(employee.getMonthlySalary()) // Use monthlySalary instead of baseSalary
                
                // Assignment info
                .assignmentId(assignment != null ? assignment.getId() : null)
                .assignmentStartDate(assignment != null ? assignment.getStartDate() : null)
                .assignmentEndDate(assignment != null ? assignment.getEndDate() : null)
                .assignmentStatus(assignment != null ? assignment.getStatus() : null)
                .salaryAtTime(assignment != null ? assignment.getSalaryAtTime() : null)
                .workDays(assignment != null ? assignment.getWorkDays() : null)
                .plannedDays(assignment != null ? assignment.getPlannedDays() : null)
                .workingDaysPerWeek(assignment != null && assignment.getWorkingDaysPerWeek() != null ? 
                        assignment.getWorkingDaysPerWeek().stream()
                                .map(Enum::name)
                                .collect(Collectors.toList()) : null)
                .additionalAllowance(assignment != null ? assignment.getAdditionalAllowance() : null)
                .assignmentDescription(assignment != null ? assignment.getDescription() : null)
                .assignmentType(assignment != null ? assignment.getAssignmentType() : null)
                .assignmentScope(assignment != null ? assignment.getScope() : null)
                .assignedByUsername(assignedBy != null ? assignedBy.getUsername() : null)
                .assignedByName(assignedBy != null ? assignedBy.getUsername() : null) // Use username as name
                
                // Contract info
                .contractId(contract != null ? contract.getId() : null)
                .contractTitle(contract != null ? contract.getDescription() : null) // Use description as title
                .contractDescription(contract != null ? contract.getDescription() : null)
                .contractStartDate(contract != null ? contract.getStartDate() : null)
                .contractEndDate(contract != null ? contract.getEndDate() : null)
                .contractValue(null) // No value field in Contract
                
                // Customer info
                .customerId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .customerEmail(customer != null ? customer.getEmail() : null)
                .customerAddress(customer != null ? customer.getAddress() : null)
                
                // Service info
                .serviceId(service != null ? service.getId() : null)
                .serviceName(service != null ? service.getTitle() : null) // Use title as name
                .serviceDescription(service != null ? service.getDescription() : null)
                .servicePrice(service != null ? service.getPrice() : null)
                
                // Verification info
                .verificationId(verification != null ? verification.getId() : null)
                .verificationStatus(verification != null ? verification.getStatus().name() : null)
                .verificationNotes(null) // No notes field in AssignmentVerification
                .verificationImages(verificationImages)
                
                // Timestamps
                .createdAt(evaluation.getCreatedAt())
                .updatedAt(evaluation.getUpdatedAt())
                .build();
    }
}
