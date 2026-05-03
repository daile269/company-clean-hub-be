package com.company.company_clean_hub_be.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.VerificationApprovalRequest;
import com.company.company_clean_hub_be.dto.request.VerificationCaptureRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentVerificationResponse;
import com.company.company_clean_hub_be.dto.response.VerificationImageResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentVerification;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.entity.VerificationImage;
import com.company.company_clean_hub_be.entity.VerificationReason;
import com.company.company_clean_hub_be.entity.VerificationStatus;
import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleReason;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.exception.ResourceNotFoundException;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AssignmentVerificationRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.repository.VerificationImageRepository;
import com.company.company_clean_hub_be.repository.WorkScheduleRepository;
import com.company.company_clean_hub_be.service.FileStorageService;
import com.company.company_clean_hub_be.service.VerificationService;
import com.company.company_clean_hub_be.service.WorkScheduleService;
import com.company.company_clean_hub_be.service.AssignmentMetricsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NEW Implementation with WorkSchedule integration
 * Separates work schedule (plan) from attendance (actual)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    private final AssignmentVerificationRepository verificationRepository;
    private final VerificationImageRepository imageRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleService workScheduleService;
    private final FileStorageService fileStorageService;
    private final AssignmentMetricsService assignmentMetricsService;

    @Override
    @Transactional
    public AssignmentVerification createVerificationRequirement(Assignment assignment, String reasonStr) {
        log.info("Creating verification requirement: assignmentId={}, reason={}", assignment.getId(), reasonStr);

        // Check if verification already exists
        Optional<AssignmentVerification> existing = verificationRepository.findByAssignmentId(assignment.getId());
        if (existing.isPresent()) {
            log.warn("Verification already exists for assignment: {}", assignment.getId());
            return existing.get();
        }

        VerificationReason reason = VerificationReason.valueOf(reasonStr);

        // Calculate maxAttempts based on previous verified schedules across all assignments
        Long previousVerifiedCount = workScheduleRepository.countVerifiedSchedulesByEmployeeAndReason(
                assignment.getEmployee().getId(), WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION);
        int maxAttempts = Math.max(1, 5 - previousVerifiedCount.intValue());
        log.info("Calculated maxAttempts for employee {}: {} (previousVerified={})",
                assignment.getEmployee().getId(), maxAttempts, previousVerifiedCount);

        AssignmentVerification verification = AssignmentVerification.builder()
                .assignment(assignment)
                .reason(reason)
                .status(VerificationStatus.PENDING)
                .maxAttempts(maxAttempts)
                .currentAttempts(0)
                .transitionToContractMode(assignment.getContract() != null && 
                    Boolean.TRUE.equals(assignment.getContract().getRequiresImageVerification()))
                .build();

        AssignmentVerification saved = verificationRepository.save(verification);
        log.info("Created verification requirement: id={}, maxAttempts={}, transitionToContractMode={}", 
            saved.getId(), saved.getMaxAttempts(), saved.getTransitionToContractMode());
        
        return saved;
    }

    @Override
    public Optional<AssignmentVerificationResponse> getVerificationByAssignmentId(Long assignmentId) {
        log.info("Getting verification for assignment: {}", assignmentId);

        Optional<AssignmentVerification> verificationOpt = verificationRepository.findByAssignmentId(assignmentId);
        if (verificationOpt.isEmpty()) {
            log.warn("No verification found for assignment: {}", assignmentId);
            return Optional.empty();
        }

        return Optional.of(mapToVerificationResponse(verificationOpt.get()));
    }

    @Override
    public List<AssignmentVerificationResponse> getPendingVerifications() {
        return verificationRepository.findPendingVerifications()
                .stream()
                .map(this::mapToVerificationResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<VerificationImageResponse> getImagesByAttendanceId(Long attendanceId) {
        return imageRepository.findByAttendanceId(attendanceId)
                .stream()
                .map(this::mapToImageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VerificationImageResponse captureVerificationImage(VerificationCaptureRequest request) {
        log.info("Capturing verification image: verificationId={}", request.getVerificationId());

        // NOTE: This method is DEPRECATED with work_schedule
        // Use WorkScheduleService.capturePhoto() instead
        // Keeping for backward compatibility
        
        throw new AppException(ErrorCode.INVALID_REQUEST, 
            "Please use /api/work-schedules/capture endpoint for photo capture");
    }

    @Override
    public List<VerificationImageResponse> getVerificationImages(Long verificationId) {
        return imageRepository.findByAssignmentVerificationIdOrderByCapturedAtDesc(verificationId)
                .stream()
                .map(this::mapToImageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AssignmentVerificationResponse approveVerification(VerificationApprovalRequest request,
            String approverUsername) {
        log.info("Approving verification: id={}, approver={}", request.getVerificationId(), approverUsername);

        AssignmentVerification verification = verificationRepository.findById(request.getVerificationId())
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + request.getVerificationId()));

        User approver = userRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + approverUsername));

        verification.setStatus(VerificationStatus.APPROVED);
        verification.setApprovedBy(approver);
        verification.setApprovedAt(LocalDateTime.now());

        verificationRepository.save(verification);

        // Handle work schedules based on transition mode
        handleVerificationApproval(verification);

        log.info("Approved verification: {}", verification.getId());
        return mapToVerificationResponse(verification);
    }

    @Override
    @Transactional
    public AssignmentVerificationResponse rejectVerification(Long verificationId, String reason,
            String approverUsername) {
        log.info("Rejecting verification: id={}, approver={}", verificationId, approverUsername);

        AssignmentVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + verificationId));

        // Reset to allow more attempts
        verification.setStatus(VerificationStatus.PENDING);
        verification.setCurrentAttempts(0);

        verificationRepository.save(verification);
        log.info("Rejected verification: {}, reset attempts", verification.getId());

        return mapToVerificationResponse(verification);
    }

    @Override
    @Transactional
    public AssignmentVerificationResponse bypassApproveVerification(Long verificationId, String notes, String approverUsername) {
        log.info("Bypass-approving verification: id={}, approver={}", verificationId, approverUsername);

        AssignmentVerification verification = verificationRepository.findByIdWithEmployee(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + verificationId));

        if (verification.isCompleted()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Verification already completed");
        }

        User approver = userRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + approverUsername));

        verification.setStatus(VerificationStatus.BYPASS_APPROVED);
        verification.setApprovedBy(approver);
        verification.setApprovedAt(LocalDateTime.now());
        verificationRepository.save(verification);

        // Reuse the same post-approval work schedule logic
        handleVerificationApproval(verification);

        log.info("Bypass-approved verification: {}", verificationId);
        return mapToVerificationResponse(verification);
    }

    @Override
    public boolean requiresVerification(Assignment assignment) {
        
        // Condition 0: Check if employee has started but NOT completed 5 NEW_EMPLOYEE_VERIFICATION photos
        // This MUST be checked BEFORE completed verification count, because bypass approval
        // counts as "completed" but the employee may not have actually taken 5 photos yet.
        Long verifiedNewEmployeeCount = workScheduleRepository.countVerifiedSchedulesByEmployeeAndReason(
                assignment.getEmployee().getId(), WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION);
        if (verifiedNewEmployeeCount > 0 && verifiedNewEmployeeCount < 5) {
            log.info("Assignment {} requires verification: employee {} has {} verified NEW_EMPLOYEE_VERIFICATION (< 5), needs to continue",
                    assignment.getId(), assignment.getEmployee().getId(), verifiedNewEmployeeCount);
            return true;
        }

        // Check approved verification — if employee already has an approved verification
        // AND has completed 5+ photos, no new verification is needed
        Long completedCount = verificationRepository.countCompletedVerificationsByEmployee(
                assignment.getEmployee().getId());
        if (completedCount > 0 && verifiedNewEmployeeCount >= 5) {
            log.info("Assignment {} does NOT require verification: employee {} has {} approved verification(s) and {} verified photos (>= 5)",
                    assignment.getId(), assignment.getEmployee().getId(), completedCount, verifiedNewEmployeeCount);
            return false;
        }

        // Condition 1: Completely new employee (never had any OTHER assignment)
        // Pass assignment.getId() to exclude the current assignment from the count
        if (isEmployeeCompletelyNew(assignment.getEmployee().getId(), assignment.getId())) {
            log.info("Assignment {} requires verification: NEW_EMPLOYEE", assignment.getId());
            return true;
        }

        // If employee has completed verification(s) but verifiedNewEmployeeCount == 0,
        // it means they were approved/bypassed without any NEW_EMPLOYEE_VERIFICATION photos
        // (edge case). Still treat as completed.
        if (completedCount > 0) {
            log.info("Assignment {} does NOT require verification: employee {} has {} approved verification(s)",
                    assignment.getId(), assignment.getEmployee().getId(), completedCount);
            return false;
        }

        // Condition 2: Contract setting
        if (assignment.getContract() != null && 
            Boolean.TRUE.equals(assignment.getContract().getRequiresImageVerification())) {
            log.info("Assignment {} requires verification: CONTRACT_REQUIREMENT", assignment.getId());
            return true;
        }

        return false;
    }

    @Override
    public boolean isEmployeeNew(Long employeeId) {
        Long completedCount = verificationRepository.countCompletedVerificationsByEmployee(employeeId);
        return completedCount == 0;
    }

    @Override
    public boolean canCaptureImage(Long verificationId) {
        // NOTE: This is deprecated with work_schedule
        // Use WorkScheduleService.canCapturePhoto() instead
        return false;
    }

    @Override
    @Transactional
    public void processAutoApprovals() {
        log.info("Starting auto-approval process...");
        
        // Find verifications that already entered capture flow.
        // Important: do not limit to IN_PROGRESS only, because some records
        // can remain PENDING even after captures happened.
        List<AssignmentVerification> verifications = verificationRepository
            .findVerificationsForAutoApproval();
        
        for (AssignmentVerification verification : verifications) {
            try {
                Long verifiedCount = workScheduleRepository.countByVerificationIdAndStatus(
                    verification.getId(), WorkScheduleStatus.VERIFIED
                );
                
                log.info("Verification {} has {} verified schedules", verification.getId(), verifiedCount);
                
                if (verifiedCount >= 5) {
                    log.info("Auto-approving verification: id={}", verification.getId());
                    
                    verification.setStatus(VerificationStatus.AUTO_APPROVED);
                    verification.setAutoApprovedAt(LocalDateTime.now());
                    verificationRepository.save(verification);
                    
                    // Handle work schedules
                    handleVerificationApproval(verification);
                    
                    log.info("Successfully auto-approved verification: {}", verification.getId());
                } else {
                    log.info("Verification {} not eligible yet (verified schedules: {})", 
                        verification.getId(), verifiedCount);
                }
            } catch (Exception e) {
                log.error("Failed to auto-approve verification: {}", verification.getId(), e);
            }
        }
        
        log.info("Auto-approval process completed");
    }

    @Override
    @Transactional
    public void syncContractVerificationState(Contract contract, boolean requiresVerification) {
        log.info("Syncing contract verification state: contractId={}, enabled={}", 
            contract.getId(), requiresVerification);
        
        List<Assignment> activeAssignments = assignmentRepository.findByContractId(contract.getId()).stream()
            .filter(a -> a.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.IN_PROGRESS || 
                        a.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.SCHEDULED)
            .collect(Collectors.toList());

        for (Assignment assignment : activeAssignments) {
            try {
                if (requiresVerification) {
                    handleContractVerificationEnabled(assignment);
                } else {
                    handleContractVerificationDisabled(assignment);
                }
            } catch (Exception e) {
                log.error("Failed to sync verification for assignment {}: {}", assignment.getId(), e.getMessage(), e);
            }
        }
    }

    // Private helper methods

    private boolean isEmployeeCompletelyNew(Long employeeId, Long excludeAssignmentId) {
        Long totalAssignments = excludeAssignmentId != null
                ? assignmentRepository.countAssignmentsByEmployeeExcluding(employeeId, excludeAssignmentId)
                : assignmentRepository.countAssignmentsByEmployee(employeeId);
        return totalAssignments == 0;
    }

    private void handleVerificationApproval(AssignmentVerification verification) {
        log.info("Handling verification approval: id={}, transitionToContractMode={}", 
            verification.getId(), verification.getTransitionToContractMode());

        // Get all work schedules for this verification
        List<WorkSchedule> schedules = workScheduleRepository.findByVerificationId(verification.getId());
        
        if (verification.getTransitionToContractMode()) {
            // Transition to CONTRACT_REQUIREMENT mode
            log.info("Transitioning to CONTRACT_REQUIREMENT mode for verification: {}", verification.getId());
            
            // For contracts with image verification:
            // - Keep SCHEDULED/MISSED verification schedules as-is (employee still needs to capture photos)
            // - Only VERIFIED schedules already have attendance (employee captured photo)
            // - Bypass only means "skip approval", NOT "skip photo capture"
            // Do NOT delete or cancel verification schedules — employee must still capture photos for those days
            // Just save any status changes for VERIFIED ones
            workScheduleRepository.saveAll(schedules);
            
            // Find the last verification schedule date to know where CONTRACT_REQUIREMENT should start
            LocalDate lastVerificationDate = schedules.stream()
                .map(WorkSchedule::getScheduledDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
            
            // Create CONTRACT_REQUIREMENT starting from the day AFTER the last verification schedule
            LocalDate contractRequirementStart = lastVerificationDate.plusDays(1);
            
            // Rest of current month (from after last verification day)
            LocalDate endOfCurrentMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
            if (!contractRequirementStart.isAfter(endOfCurrentMonth)) {
                workScheduleService.createWorkSchedulesForAssignment(
                    verification.getAssignment(),
                    WorkScheduleReason.CONTRACT_REQUIREMENT,
                    null,
                    contractRequirementStart,
                    endOfCurrentMonth
                );
                log.info("Created CONTRACT_REQUIREMENT schedules for rest of current month: {} to {}",
                    contractRequirementStart, endOfCurrentMonth);
            }

            // Next month
            LocalDate nextMonth = LocalDate.now().plusMonths(1);
            LocalDate endOfNextMonth = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
            workScheduleService.createWorkSchedulesForAssignment(
                verification.getAssignment(),
                WorkScheduleReason.CONTRACT_REQUIREMENT,
                null,
                nextMonth.withDayOfMonth(1),
                endOfNextMonth
            );
            
        } else {
            // No transition - complete verification (contract doesn't require verification)
            log.info("Completing verification without transition: {}", verification.getId());
            
            // Create attendances for ALL verification schedules (SCHEDULED + MISSED)
            for (WorkSchedule schedule : schedules) {
                if (schedule.getAttendance() == null) {
                    Attendance attendance = createAttendanceFromSchedule(schedule);
                    schedule.setAttendance(attendance);
                }
                schedule.setStatus(WorkScheduleStatus.VERIFIED);
                schedule.setSyncNote("Approved - verification completed");
                schedule.setLastSyncedAt(LocalDateTime.now());
            }
            workScheduleRepository.saveAll(schedules);
            log.info("Created attendances for {} verification work schedules", schedules.size());
            
            // Create attendance for gap period: [assignmentStartDate, firstVerificationDate - 1]
            // This handles the case where employee was assigned mid-month but assignment.startDate
            // is at the beginning of the month (e.g. contract start). The verification WorkSchedules
            // only start from the actual assignment creation date, leaving a gap.
            Assignment gapAssignment = verification.getAssignment();
            if (!schedules.isEmpty() && gapAssignment.getStartDate() != null) {
                LocalDate firstVerificationDate = schedules.stream()
                        .map(WorkSchedule::getScheduledDate)
                        .min(LocalDate::compareTo)
                        .orElse(null);
                if (firstVerificationDate != null && gapAssignment.getStartDate().isBefore(firstVerificationDate)) {
                    LocalDate gapStart = gapAssignment.getStartDate();
                    LocalDate gapEnd = firstVerificationDate.minusDays(1);
                    List<java.time.DayOfWeek> gapWorkingDays = gapAssignment.getWorkingDaysPerWeek();
                    if (gapWorkingDays != null && !gapWorkingDays.isEmpty() && !gapStart.isAfter(gapEnd)) {
                        List<Attendance> gapAttendances = new ArrayList<>();
                        LocalDate gapCurrent = gapStart;
                        while (!gapCurrent.isAfter(gapEnd)) {
                            if (gapWorkingDays.contains(gapCurrent.getDayOfWeek())) {
                                boolean exists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                        gapAssignment.getId(), gapAssignment.getEmployee().getId(), gapCurrent).isPresent();
                                if (!exists) {
                                    gapAttendances.add(Attendance.builder()
                                            .assignment(gapAssignment)
                                            .employee(gapAssignment.getEmployee())
                                            .date(gapCurrent)
                                            .workHours(java.math.BigDecimal.valueOf(8))
                                            .deleted(false)
                                            .bonus(java.math.BigDecimal.ZERO)
                                            .penalty(java.math.BigDecimal.ZERO)
                                            .supportCost(java.math.BigDecimal.ZERO)
                                            .isOvertime(false)
                                            .overtimeAmount(java.math.BigDecimal.ZERO)
                                            .description("Tự động tạo khi approve verification (gap trước ngày bắt đầu verification)")
                                            .createdAt(LocalDateTime.now())
                                            .updatedAt(LocalDateTime.now())
                                            .build());
                                }
                            }
                            gapCurrent = gapCurrent.plusDays(1);
                        }
                        if (!gapAttendances.isEmpty()) {
                            attendanceRepository.saveAll(gapAttendances);
                            log.info("Created {} gap attendances for period before verification: {} to {} for assignmentId={}",
                                    gapAttendances.size(), gapStart, gapEnd, gapAssignment.getId());
                        }
                    }
                }
            }

            // Create attendance DIRECTLY for remaining working days after verification
            // (instead of creating AUTO_ATTENDANCE WorkSchedules)
            // Bắt đầu từ ngày sau verification cuối cùng (không phải tomorrow) để bao gồm cả ngày quá khứ
            LocalDate lastVerificationDate = schedules.stream()
                .map(WorkSchedule::getScheduledDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
            LocalDate afterVerification = lastVerificationDate.plusDays(1);
            LocalDate endOfCurrentMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
            
            // Respect assignment end date (for SUPPORT assignments with specific dates)
            Assignment assignment = verification.getAssignment();
            if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(endOfCurrentMonth)) {
                endOfCurrentMonth = assignment.getEndDate();
                log.info("Using assignment.endDate as limit: {}", endOfCurrentMonth);
            }
            
            // Respect contract end date if it falls before end of current month
            Contract contract = assignment.getContract();
            if (contract != null && contract.getEndDate() != null && contract.getEndDate().isBefore(endOfCurrentMonth)) {
                endOfCurrentMonth = contract.getEndDate();
                log.info("Using contract.endDate as limit: {}", endOfCurrentMonth);
            }
            
            // Create attendance for all working days from after verification to end of current month
            if (!afterVerification.isAfter(endOfCurrentMonth)) {
                List<java.time.DayOfWeek> workingDays = assignment.getWorkingDaysPerWeek();
                if (workingDays != null && !workingDays.isEmpty()) {
                    LocalDate current = afterVerification;
                    List<Attendance> toCreate = new ArrayList<>();
                    while (!current.isAfter(endOfCurrentMonth)) {
                        if (workingDays.contains(current.getDayOfWeek())) {
                            boolean exists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                assignment.getId(), assignment.getEmployee().getId(), current).isPresent();
                            if (!exists) {
                                toCreate.add(Attendance.builder()
                                    .assignment(assignment)
                                    .employee(assignment.getEmployee())
                                    .date(current)
                                    .workHours(java.math.BigDecimal.valueOf(8))
                                    .deleted(false)
                                    .bonus(java.math.BigDecimal.ZERO)
                                    .penalty(java.math.BigDecimal.ZERO)
                                    .supportCost(java.math.BigDecimal.ZERO)
                                    .isOvertime(false)
                                    .overtimeAmount(java.math.BigDecimal.ZERO)
                                    .description("Tự động tạo khi approve verification (không cần xác minh hình ảnh)")
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build());
                            }
                        }
                        current = current.plusDays(1);
                    }
                    if (!toCreate.isEmpty()) {
                        attendanceRepository.saveAll(toCreate);
                        int newWorkDays = (assignment.getWorkDays() != null ? assignment.getWorkDays() : 0) + toCreate.size();
                        assignment.setWorkDays(newWorkDays);
                        // No explicit save needed — entity is managed within @Transactional,
                        // Hibernate dirty checking will flush the workDays update automatically
                        log.info("Created {} attendances directly for remaining working days after verification: {} to {}. " +
                                 "Future months will be handled by VerificationScheduler.generateMonthlyWorkSchedules()",
                            toCreate.size(), afterVerification, endOfCurrentMonth);
                    }
                } else {
                    log.info("No working days configured for assignment {}", assignment.getId());
                }
            } else {
                log.info("No remaining days in current month after verification (afterVerification={}, endOfMonth={}). " +
                         "Future months will be handled by VerificationScheduler.generateMonthlyWorkSchedules()",
                    afterVerification, endOfCurrentMonth);
            }
        }
        
        // NOTE: GAP PERIOD ATTENDANCE (contractStartDate → assignmentStartDate) đã bị xóa.
        // Nhân viên chỉ có attendance từ assignment.startDate trở đi.
        // Gap từ assignment.startDate → firstVerificationDate đã được xử lý ở trên (trong non-transition path).

        // Update assignment metrics after approval
        assignmentMetricsService.updateAssignmentMetrics(verification.getAssignment().getId());
    }

    private void handleContractVerificationEnabled(Assignment assignment) {
        log.info("[VERIFI-ENABLE] ===== START handleContractVerificationEnabled =====");
        log.info("[VERIFI-ENABLE] assignmentId={}, employeeId={}, contractId={}", 
            assignment.getId(), 
            assignment.getEmployee() != null ? assignment.getEmployee().getId() : "NULL",
            assignment.getContract() != null ? assignment.getContract().getId() : "NULL");
        
        LocalDate today = LocalDate.now();
        log.info("[VERIFI-ENABLE] today={}", today);

        // Xóa work_schedules SCHEDULED cũ (từ hôm nay trở đi) trước khi tạo mới
        List<WorkSchedule> oldScheduled = workScheduleRepository.findByAssignmentId(assignment.getId())
            .stream()
            .filter(ws -> ws.getStatus() == WorkScheduleStatus.SCHEDULED 
                       && !ws.getScheduledDate().isBefore(today))
            .collect(Collectors.toList());
        log.info("[VERIFI-ENABLE] Found {} old SCHEDULED work_schedules to delete (from {} onwards)", 
            oldScheduled.size(), today);
        oldScheduled.forEach(ws -> log.info("[VERIFI-ENABLE]   - workScheduleId={}, date={}, status={}", 
            ws.getId(), ws.getScheduledDate(), ws.getStatus()));
        if (!oldScheduled.isEmpty()) {
            workScheduleRepository.deleteAll(oldScheduled);
            log.info("[VERIFI-ENABLE] Deleted {} old SCHEDULED work_schedules", oldScheduled.size());
        }

        // Đếm attendance trước khi xóa
        Long attendancesBefore = attendanceRepository.countAttendancesByAssignment(assignment.getId());
        log.info("[VERIFI-ENABLE] Attendance count BEFORE delete: {}", attendancesBefore);

        // Xóa attendance từ hôm nay trở đi
        attendanceRepository.deleteByAssignmentIdAndDateAfter(assignment.getId(), today.minusDays(1));

        Long attendancesAfter = attendanceRepository.countAttendancesByAssignment(assignment.getId());
        log.info("[VERIFI-ENABLE] Attendance count AFTER delete: {} (deleted {})", 
            attendancesAfter, attendancesBefore - attendancesAfter);

        // Cập nhật workDays
        assignment.setWorkDays(attendancesAfter != null ? attendancesAfter.intValue() : 0);
        assignmentRepository.save(assignment);
        log.info("[VERIFI-ENABLE] Updated assignment workDays={}", assignment.getWorkDays());

        // Reload để tránh lazy loading issue với workingDaysPerWeek
        Assignment freshAssignment = assignmentRepository.findById(assignment.getId()).orElse(assignment);
        log.info("[VERIFI-ENABLE] freshAssignment workingDaysPerWeek: {} (size={})", 
            freshAssignment.getWorkingDaysPerWeek(),
            freshAssignment.getWorkingDaysPerWeek() != null ? freshAssignment.getWorkingDaysPerWeek().size() : "NULL");

        // Tạo work_schedules từ hôm nay
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        log.info("[VERIFI-ENABLE] Creating work_schedules from {} to {}", today, endOfMonth);
        
        List<WorkSchedule> created = workScheduleService.createWorkSchedulesForAssignment(
            freshAssignment,
            WorkScheduleReason.CONTRACT_REQUIREMENT,
            null,
            today,
            endOfMonth
        );
        
        log.info("[VERIFI-ENABLE] Created {} work_schedules", created != null ? created.size() : 0);
        if (created != null) {
            created.forEach(ws -> log.info("[VERIFI-ENABLE]   + workScheduleId={}, date={}, status={}", 
                ws.getId(), ws.getScheduledDate(), ws.getStatus()));
        }
        log.info("[VERIFI-ENABLE] ===== END handleContractVerificationEnabled =====");
    }

    private void handleContractVerificationDisabled(Assignment assignment) {
        log.info("Disabling verification for assignment: {}", assignment.getId());

        LocalDate today = LocalDate.now();

        // Cancel pending verifications (NEW_EMPLOYEE chưa hoàn thành)
        verificationRepository.findByAssignmentId(assignment.getId()).ifPresent(verification -> {
            if (verification.getStatus() == VerificationStatus.PENDING || 
                verification.getStatus() == VerificationStatus.IN_PROGRESS) {
                verification.setStatus(VerificationStatus.CANCELLED);
                verification.setCancelledAt(LocalDateTime.now());
                verification.setCancelledReason("Contract disabled verification");
                verificationRepository.save(verification);
            }
        });

        // Xóa work_schedules SCHEDULED từ hôm nay trở đi (không cần chụp ảnh nữa)
        List<WorkSchedule> scheduledFuture = workScheduleRepository.findByAssignmentId(assignment.getId())
            .stream()
            .filter(ws -> ws.getStatus() == WorkScheduleStatus.SCHEDULED
                       && !ws.getScheduledDate().isBefore(today))
            .collect(Collectors.toList());
        if (!scheduledFuture.isEmpty()) {
            workScheduleRepository.deleteAll(scheduledFuture);
            log.info("Deleted {} SCHEDULED work_schedules for assignment {}", scheduledFuture.size(), assignment.getId());
        }

        // Sinh attendance cho các ngày từ hôm nay đến cuối tháng (theo lịch làm việc)
        Assignment freshAssignment = assignmentRepository.findById(assignment.getId()).orElse(assignment);
        List<java.time.DayOfWeek> workingDays = freshAssignment.getWorkingDaysPerWeek();
        if (workingDays != null && !workingDays.isEmpty()) {
            LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
            LocalDate current = today;
            List<Attendance> toCreate = new ArrayList<>();
            while (!current.isAfter(endOfMonth)) {
                if (workingDays.contains(current.getDayOfWeek())) {
                    boolean exists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                        freshAssignment.getId(), freshAssignment.getEmployee().getId(), current).isPresent();
                    if (!exists) {
                        toCreate.add(Attendance.builder()
                            .assignment(freshAssignment)
                            .employee(freshAssignment.getEmployee())
                            .date(current)
                            .workHours(java.math.BigDecimal.valueOf(8))
                            .deleted(false)
                            .bonus(java.math.BigDecimal.ZERO)
                            .penalty(java.math.BigDecimal.ZERO)
                            .supportCost(java.math.BigDecimal.ZERO)
                            .isOvertime(false)
                            .overtimeAmount(java.math.BigDecimal.ZERO)
                            .description("Tự động tạo khi tắt xác minh hình ảnh")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build());
                    }
                }
                current = current.plusDays(1);
            }
            if (!toCreate.isEmpty()) {
                attendanceRepository.saveAll(toCreate);
                int newWorkDays = (freshAssignment.getWorkDays() != null ? freshAssignment.getWorkDays() : 0) + toCreate.size();
                freshAssignment.setWorkDays(newWorkDays);
                assignmentRepository.save(freshAssignment);
                log.info("Created {} attendances after disabling verification for assignment {}", toCreate.size(), assignment.getId());
            }
        }

        log.info("Disabled verification for assignment: {}", assignment.getId());
    }

    private Attendance createAttendanceFromSchedule(WorkSchedule schedule) {
        Attendance attendance = Attendance.builder()
            .assignment(schedule.getAssignment())
            .employee(schedule.getEmployee())
            .date(schedule.getScheduledDate())
            .workHours(java.math.BigDecimal.valueOf(8))
            .bonus(java.math.BigDecimal.ZERO)
            .penalty(java.math.BigDecimal.ZERO)
            .supportCost(java.math.BigDecimal.ZERO)
            .isOvertime(false)
            .deleted(false)
            .overtimeAmount(java.math.BigDecimal.ZERO)
            .assignmentVerification(schedule.getAssignmentVerification())
            .description("Created from work schedule")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return attendanceRepository.save(attendance);
    }

    private AssignmentVerificationResponse mapToVerificationResponse(AssignmentVerification verification) {
        Assignment assignment = verification.getAssignment();
        Employee employee = assignment.getEmployee();

        // maxAttempts hiển thị = số WorkSchedule thực tế của verification này
        // (không phải 5 - previousVerifiedCount toàn cục, vì SUPPORT 1 ngày chỉ có 1 schedule)
        Long actualScheduleCount = workScheduleRepository.countByVerificationId(verification.getId());
        int displayMaxAttempts = actualScheduleCount != null && actualScheduleCount > 0
                ? actualScheduleCount.intValue()
                : verification.getMaxAttempts();
        
        return AssignmentVerificationResponse.builder()
                .id(verification.getId())
                .assignmentId(assignment.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .employeeCode(employee.getEmployeeCode())
                .contractId(assignment.getContract() != null ? assignment.getContract().getId() : null)
                .reason(verification.getReason())
                .status(verification.getStatus())
                .maxAttempts(displayMaxAttempts)
                .currentAttempts(verification.getCurrentAttempts())
                .approvedBy(verification.getApprovedBy() != null ? verification.getApprovedBy().getUsername() : null)
                .approvedAt(verification.getApprovedAt())
                .autoApprovedAt(verification.getAutoApprovedAt())
                .isCompleted(verification.isCompleted())
                .canCapture(verification.canCapture())
                .createdAt(verification.getCreatedAt())
                .updatedAt(verification.getUpdatedAt())
                .build();
    }

    private VerificationImageResponse mapToImageResponse(VerificationImage image) {
        return VerificationImageResponse.builder()
                .id(image.getId())
                .verificationId(image.getAssignmentVerification() != null ? image.getAssignmentVerification().getId() : null)
                .employeeId(image.getEmployee().getId())
                .attendanceId(image.getAttendance() != null ? image.getAttendance().getId() : null)
                .cloudinaryPublicId(image.getCloudinaryPublicId())
                .cloudinaryUrl(image.getCloudinaryUrl())
                .latitude(image.getLatitude())
                .longitude(image.getLongitude())
                .address(image.getAddress())
                .capturedAt(image.getCapturedAt())
                .faceConfidence(image.getFaceConfidence())
                .imageQualityScore(image.getImageQualityScore())
                .createdAt(image.getCreatedAt())
                .build();
    }
    
    @Override
    public boolean isAttendancePhoto(VerificationImage image) {
        // Ảnh chấm công: có liên kết với attendance_id
        // Được chụp để chấm công hàng ngày
        return image.getAttendance() != null;
    }
    
    @Override
    public boolean isVerificationImage(VerificationImage image) {
        // Ảnh xác minh: có liên kết với assignment_verification_id
        // Được chụp để xác minh nhân viên mới hoặc theo yêu cầu hợp đồng
        return image.getAssignmentVerification() != null;
    }
}