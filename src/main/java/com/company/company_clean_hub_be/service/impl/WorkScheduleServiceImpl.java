package com.company.company_clean_hub_be.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.WorkScheduleCaptureRequest;
import com.company.company_clean_hub_be.dto.response.WorkScheduleResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.AssignmentVerification;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.DayOfWeek;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.VerificationImage;
import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleReason;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.exception.ResourceNotFoundException;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AssignmentVerificationRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.VerificationImageRepository;
import com.company.company_clean_hub_be.repository.WorkScheduleRepository;
import com.company.company_clean_hub_be.service.FileStorageService;
import com.company.company_clean_hub_be.service.WorkScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkScheduleServiceImpl implements WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentVerificationRepository verificationRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final VerificationImageRepository imageRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public List<WorkSchedule> createWorkSchedulesForAssignment(
            Assignment assignment,
            WorkScheduleReason reason,
            Long verificationId,
            LocalDate fromDate,
            LocalDate toDate) {
        
        log.info("Creating work schedules: assignmentId={}, reason={}, from={}, to={}", 
            assignment.getId(), reason, fromDate, toDate);

        List<WorkSchedule> schedules = new ArrayList<>();
        AssignmentVerification verification = null;
        
        if (verificationId != null) {
            verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + verificationId));
        }

        // Get working days from assignment - convert entity DayOfWeek to java.time.DayOfWeek
        List<java.time.DayOfWeek> workingDays;
        List<java.time.DayOfWeek> rawDays = assignment.getWorkingDaysPerWeek();
        log.info("[CREATE-WS] workingDays from assignment: {} (size={})", 
            rawDays, rawDays != null ? rawDays.size() : "NULL");
        
        if (rawDays == null || rawDays.isEmpty()) {
            log.info("[CREATE-WS] workingDays is empty, using default Mon-Sat");
            workingDays = List.of(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY,
                java.time.DayOfWeek.FRIDAY,
                java.time.DayOfWeek.SATURDAY
            );
        } else {
            workingDays = rawDays;
            log.info("[CREATE-WS] workingDays: {}", workingDays);
        }

        LocalDate currentDate = fromDate;
        while (!currentDate.isAfter(toDate)) {
            java.time.DayOfWeek javaDayOfWeek = currentDate.getDayOfWeek();
            boolean isWorkingDay = workingDays.contains(javaDayOfWeek);
            boolean alreadyExists = workScheduleRepository.existsByAssignmentIdAndScheduledDate(assignment.getId(), currentDate);
            log.info("[CREATE-WS] date={}, dayOfWeek={}, isWorkingDay={}, alreadyExists={}", 
                currentDate, javaDayOfWeek, isWorkingDay, alreadyExists);

            if (isWorkingDay) {
                // Check if already exists
                if (!workScheduleRepository.existsByAssignmentIdAndScheduledDate(assignment.getId(), currentDate)) {
                    WorkSchedule schedule = WorkSchedule.builder()
                        .assignment(assignment)
                        .employee(assignment.getEmployee())
                        .scheduledDate(currentDate)
                        .status(WorkScheduleStatus.SCHEDULED)
                        .reason(reason)
                        .assignmentVerification(verification)
                        .build();
                    
                    schedules.add(schedule);
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        List<WorkSchedule> saved = workScheduleRepository.saveAll(schedules);
        log.info("Created {} work schedules for assignment {}", saved.size(), assignment.getId());
        
        return saved;
    }

    @Override
    public List<WorkScheduleResponse> getWorkSchedulesByAssignment(Long assignmentId) {
        List<WorkSchedule> schedules = workScheduleRepository.findByAssignmentId(assignmentId);
        return schedules.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    public List<WorkScheduleResponse> getWorkSchedulesByEmployee(Long employeeId, LocalDate startDate, LocalDate endDate) {
        List<WorkSchedule> schedules = workScheduleRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
        return schedules.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    public WorkScheduleResponse getWorkScheduleById(Long id) {
        WorkSchedule schedule = workScheduleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Work schedule not found: " + id));
        return mapToResponse(schedule);
    }
    
    @Override
    public List<WorkScheduleResponse> getMissedSchedules(LocalDate startDate, LocalDate endDate) {
        List<WorkSchedule> schedules = workScheduleRepository.findMissedSchedulesByDateRange(startDate, endDate);
        return schedules.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<WorkScheduleResponse> getMissedSchedulesByEmployee(Long employeeId, LocalDate startDate, LocalDate endDate) {
        List<WorkSchedule> schedules = workScheduleRepository.findMissedSchedulesByEmployeeAndDateRange(employeeId, startDate, endDate);
        return schedules.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<WorkScheduleResponse> getWorkSchedulesByDateRange(LocalDate startDate, LocalDate endDate, Long employeeId, String status) {
        log.info("Getting work schedules by date range: {} to {}, employeeId: {}, status: {}", 
            startDate, endDate, employeeId, status);
        
        List<WorkSchedule> schedules;
        
        if (employeeId != null) {
            schedules = workScheduleRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
        } else {
            // Get all schedules in date range
            schedules = workScheduleRepository.findAll().stream()
                .filter(ws -> !ws.getScheduledDate().isBefore(startDate) && !ws.getScheduledDate().isAfter(endDate))
                .collect(Collectors.toList());
        }
        
        // Filter by status if provided
        if (status != null && !status.isEmpty()) {
            try {
                WorkScheduleStatus statusEnum = WorkScheduleStatus.valueOf(status.toUpperCase());
                schedules = schedules.stream()
                    .filter(ws -> ws.getStatus() == statusEnum)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status: {}", status);
            }
        }
        
        return schedules.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<WorkScheduleResponse> getWorkSchedulesByDate(LocalDate date, String status) {
        log.info("Getting work schedules by date: {}, status: {}", date, status);
        
        List<WorkSchedule> schedules = workScheduleRepository.findByScheduledDate(date);
        
        // Filter by status if provided
        if (status != null && !status.isEmpty()) {
            try {
                WorkScheduleStatus statusEnum = WorkScheduleStatus.valueOf(status.toUpperCase());
                schedules = schedules.stream()
                    .filter(ws -> ws.getStatus() == statusEnum)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status: {}", status);
            }
        }
        
        return schedules.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Override
    public com.company.company_clean_hub_be.dto.response.WorkScheduleStatsResponse getStats(Integer month, Integer year, Long employeeId) {
        log.info("Getting work schedule stats: month={}, year={}, employeeId={}", month, year, employeeId);
        
        // Determine date range
        LocalDate startDate;
        LocalDate endDate;
        
        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }
        
        // Get schedules
        List<WorkSchedule> schedules;
        if (employeeId != null) {
            schedules = workScheduleRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
        } else {
            schedules = workScheduleRepository.findAll().stream()
                .filter(ws -> !ws.getScheduledDate().isBefore(startDate) && !ws.getScheduledDate().isAfter(endDate))
                .collect(Collectors.toList());
        }
        
        // Calculate stats
        long total = schedules.size();
        long verified = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.VERIFIED).count();
        long missed = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.MISSED).count();
        long scheduled = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.SCHEDULED).count();
        long cancelled = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.CANCELLED).count();
        
        double verifiedPercentage = total > 0 ? (verified * 100.0 / total) : 0.0;
        double missedPercentage = total > 0 ? (missed * 100.0 / total) : 0.0;
        double scheduledPercentage = total > 0 ? (scheduled * 100.0 / total) : 0.0;
        
        return com.company.company_clean_hub_be.dto.response.WorkScheduleStatsResponse.builder()
            .total(total)
            .verified(verified)
            .missed(missed)
            .scheduled(scheduled)
            .cancelled(cancelled)
            .verifiedPercentage(Math.round(verifiedPercentage * 100.0) / 100.0)
            .missedPercentage(Math.round(missedPercentage * 100.0) / 100.0)
            .scheduledPercentage(Math.round(scheduledPercentage * 100.0) / 100.0)
            .build();
    }
    
    @Override
    public List<com.company.company_clean_hub_be.dto.response.EmployeeScheduleSummary> getEmployeesWithSchedules(Integer month, Integer year) {
        log.info("Getting employees with schedules: month={}, year={}", month, year);
        
        // Determine date range
        LocalDate startDate;
        LocalDate endDate;
        
        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }
        
        // Get all schedules in date range
        List<WorkSchedule> schedules = workScheduleRepository.findAll().stream()
            .filter(ws -> !ws.getScheduledDate().isBefore(startDate) && !ws.getScheduledDate().isAfter(endDate))
            .collect(Collectors.toList());
        
        // Group by employee
        return schedules.stream()
            .collect(Collectors.groupingBy(WorkSchedule::getEmployee))
            .entrySet().stream()
            .map(entry -> {
                Employee employee = entry.getKey();
                List<WorkSchedule> employeeSchedules = entry.getValue();
                
                long total = employeeSchedules.size();
                long verified = employeeSchedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.VERIFIED).count();
                long missed = employeeSchedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.MISSED).count();
                long scheduled = employeeSchedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.SCHEDULED).count();
                
                return com.company.company_clean_hub_be.dto.response.EmployeeScheduleSummary.builder()
                    .employeeId(employee.getId())
                    .employeeName(employee.getName())
                    .employeeCode(employee.getEmployeeCode())
                    .totalSchedules(total)
                    .verifiedCount(verified)
                    .missedCount(missed)
                    .scheduledCount(scheduled)
                    .build();
            })
            .sorted((a, b) -> a.getEmployeeName().compareTo(b.getEmployeeName()))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkScheduleResponse capturePhoto(WorkScheduleCaptureRequest request) {
        log.info("Capturing photo for work schedule: {}", request.getWorkScheduleId());

        WorkSchedule schedule = workScheduleRepository.findById(request.getWorkScheduleId())
            .orElseThrow(() -> new ResourceNotFoundException("Work schedule not found: " + request.getWorkScheduleId()));

        // Validate
        if (schedule.getStatus() == WorkScheduleStatus.VERIFIED) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Work schedule already verified");
        }

        if (schedule.getStatus() == WorkScheduleStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Work schedule is cancelled");
        }

        if (!schedule.getScheduledDate().equals(LocalDate.now())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Can only capture photo for today's schedule");
        }

        // Save image
        VerificationImage image = saveVerificationImage(request, schedule);

        // Update work schedule
        schedule.setStatus(WorkScheduleStatus.VERIFIED);
        schedule.setPhotoCapturedAt(LocalDateTime.now());
        schedule.setVerificationImage(image);

        // Create attendance
        Attendance attendance = createAttendanceFromSchedule(schedule);
        schedule.setAttendance(attendance);
        
        // Link verification image to attendance for attendance photos
        if (image != null && attendance != null) {
            image.setAttendance(attendance);
            imageRepository.save(image);
        }
        
        schedule.setLastSyncedAt(LocalDateTime.now());
        schedule.setSyncNote("Attendance created from photo capture");

        workScheduleRepository.save(schedule);

        log.info("Photo captured successfully for work schedule: {}", schedule.getId());

        // Update assignment workDays (+1 for this capture)
        try {
            Assignment assignment = schedule.getAssignment();
            if (assignment != null) {
                int currentWorkDays = assignment.getWorkDays() != null ? assignment.getWorkDays() : 0;
                assignment.setWorkDays(currentWorkDays + 1);
                assignmentRepository.save(assignment);
            }
        } catch (Exception e) {
            log.error("Failed to update workDays for assignment: {}", e.getMessage());
        }

        // Check auto-approval if NEW_EMPLOYEE_VERIFICATION
        if (schedule.getReason() == WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION && 
            schedule.getAssignmentVerification() != null) {
            checkAndAutoApprove(schedule.getAssignmentVerification().getId());
        } else if (schedule.getReason() == WorkScheduleReason.CONTRACT_REQUIREMENT) {
            // CONTRACT_REQUIREMENT: no auto-approve needed, but update plannedDays if not set
            updateAssignmentDaysAfterApproval(schedule.getAssignment().getId());
        }

        return mapToResponse(schedule);
    }

    @Override
    @Transactional
    public void markMissedCheckIns(LocalDate date) {
        log.info("Marking missed check-ins for date: {}", date);

        List<WorkSchedule> scheduledToday = workScheduleRepository
            .findByScheduledDateAndStatus(date, WorkScheduleStatus.SCHEDULED);

        for (WorkSchedule schedule : scheduledToday) {
            schedule.setStatus(WorkScheduleStatus.MISSED);
            schedule.setSyncNote("Missed check-in for " + date);
            log.warn("Marked MISSED: workScheduleId={}, employeeId={}, date={}", 
                schedule.getId(), schedule.getEmployee().getId(), date);
        }

        workScheduleRepository.saveAll(scheduledToday);
        log.info("Marked {} schedules as MISSED", scheduledToday.size());
    }

    @Override
    @Transactional
    public void syncAttendanceDeletion(Long attendanceId, Long userId) {
        log.info("Syncing attendance deletion: attendanceId={}, userId={}", attendanceId, userId);

        workScheduleRepository.findByAttendanceId(attendanceId).ifPresent(schedule -> {
            schedule.setAttendance(null);
            schedule.setAttendanceDeleted(true);
            schedule.setSyncNote("Attendance deleted at " + LocalDateTime.now() + " by user " + userId);
            schedule.setLastSyncedAt(LocalDateTime.now());
            workScheduleRepository.save(schedule);
            
            log.info("Synced work schedule {} with attendance deletion", schedule.getId());
        });
    }

    @Override
    @Transactional
    public void syncAttendanceCreation(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Attendance not found: " + attendanceId));

        workScheduleRepository.findByAssignmentIdAndScheduledDate(
            attendance.getAssignment().getId(), 
            attendance.getDate()
        ).ifPresent(schedule -> {
            schedule.setAttendance(attendance);
            schedule.setStatus(WorkScheduleStatus.VERIFIED);
            schedule.setAttendanceDeleted(false);
            schedule.setSyncNote("Attendance created manually");
            schedule.setLastSyncedAt(LocalDateTime.now());
            workScheduleRepository.save(schedule);
            
            log.info("Synced work schedule {} with attendance creation", schedule.getId());
        });
    }

    @Override
    @Transactional
    public void generateMonthlyWorkSchedules(LocalDate month) {
        log.info("Generating monthly work schedules for: {}", month);
        
        LocalDate startDate = month.withDayOfMonth(1);
        LocalDate endDate = month.withDayOfMonth(month.lengthOfMonth());
        
        // Find all active assignments
        List<Assignment> activeAssignments = assignmentRepository.findAll().stream()
            .filter(a -> a.getStatus() == AssignmentStatus.IN_PROGRESS || a.getStatus() == AssignmentStatus.SCHEDULED)
            .filter(a -> {
                // Check if assignment is still active in this month
                LocalDate assignmentStart = a.getStartDate();
                LocalDate assignmentEnd = a.getEndDate();
                
                // Assignment overlaps with month
                return !assignmentStart.isAfter(endDate) && 
                       (assignmentEnd == null || !assignmentEnd.isBefore(startDate));
            })
            .collect(Collectors.toList());
        
        log.info("Found {} active assignments for month {}", activeAssignments.size(), month);
        
        int totalCreated = 0;
        for (Assignment assignment : activeAssignments) {
            try {
                // Check if requires verification
                boolean requiresVerification = requiresVerification(assignment);
                
                if (requiresVerification) {
                    // Determine reason
                    boolean isNewEmployee = isEmployeeNew(assignment.getEmployee().getId());
                    WorkScheduleReason reason = isNewEmployee ? 
                        WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION : 
                        WorkScheduleReason.CONTRACT_REQUIREMENT;
                    
                    // Get verification if exists
                    Long verificationId = null;
                    if (isNewEmployee) {
                        verificationId = verificationRepository
                            .findByAssignmentId(assignment.getId())
                            .map(AssignmentVerification::getId)
                            .orElse(null);
                    }
                    
                    // Calculate date range for this assignment
                    LocalDate assignmentStartInMonth = assignment.getStartDate().isAfter(startDate) ? 
                        assignment.getStartDate() : startDate;
                    LocalDate assignmentEndInMonth = endDate;
                    
                    if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(endDate)) {
                        assignmentEndInMonth = assignment.getEndDate();
                    }
                    
                    // Create work schedules
                    List<WorkSchedule> created = createWorkSchedulesForAssignment(
                        assignment,
                        reason,
                        verificationId,
                        assignmentStartInMonth,
                        assignmentEndInMonth
                    );
                    
                    totalCreated += created.size();
                    log.info("Created {} work schedules for assignment {}", created.size(), assignment.getId());
                }
            } catch (Exception e) {
                log.error("Failed to generate work schedules for assignment {}: {}", 
                    assignment.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Monthly generation complete: created {} work schedules for month {}", totalCreated, month);
    }
    
    private boolean requiresVerification(Assignment assignment) {
        // Check if employee is completely new
        if (isEmployeeNew(assignment.getEmployee().getId())) {
            return true;
        }
        
        // Check contract setting
        if (assignment.getContract() != null && 
            Boolean.TRUE.equals(assignment.getContract().getRequiresImageVerification())) {
            return true;
        }
        
        return false;
    }
    
    private boolean isEmployeeNew(Long employeeId) {
        Long totalAssignments = assignmentRepository.countAssignmentsByEmployee(employeeId);
        return totalAssignments == 0;
    }

    @Override
    @Transactional
    public WorkScheduleResponse cancelWorkSchedule(Long id, String reason) {
        WorkSchedule schedule = workScheduleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Work schedule not found: " + id));

        schedule.setStatus(WorkScheduleStatus.CANCELLED);
        schedule.setSyncNote("Cancelled: " + reason);
        schedule.setLastSyncedAt(LocalDateTime.now());

        workScheduleRepository.save(schedule);
        log.info("Cancelled work schedule: {}", id);

        return mapToResponse(schedule);
    }

    @Override
    @Transactional
    public WorkScheduleResponse createAttendanceForMissed(Long id, String reason) {
        WorkSchedule schedule = workScheduleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Work schedule not found: " + id));

        if (schedule.getStatus() != WorkScheduleStatus.MISSED) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Work schedule is not in MISSED status");
        }

        // Create attendance
        Attendance attendance = createAttendanceFromSchedule(schedule);
        schedule.setAttendance(attendance);
        schedule.setStatus(WorkScheduleStatus.VERIFIED);
        schedule.setSyncNote("Attendance created manually for missed schedule: " + reason);
        schedule.setLastSyncedAt(LocalDateTime.now());

        workScheduleRepository.save(schedule);
        log.info("Created attendance for missed work schedule: {}", id);

        return mapToResponse(schedule);
    }

    @Override
    public boolean canCapturePhoto(Long workScheduleId) {
        return workScheduleRepository.findById(workScheduleId)
            .map(WorkSchedule::canCapturePhoto)
            .orElse(false);
    }
    
    @Override
    @Transactional
    public void handleAssignmentUpdate(Long assignmentId, LocalDate newStartDate, LocalDate newEndDate) {
        log.info("Handling assignment update: assignmentId={}, newStart={}, newEnd={}", 
            assignmentId, newStartDate, newEndDate);
        
        // Cancel all future work schedules (after today)
        LocalDate today = LocalDate.now();
        List<WorkSchedule> futureSchedules = workScheduleRepository
            .findByAssignmentIdAndScheduledDateAfter(assignmentId, today);
        
        for (WorkSchedule schedule : futureSchedules) {
            if (schedule.getStatus() == WorkScheduleStatus.SCHEDULED) {
                schedule.setStatus(WorkScheduleStatus.CANCELLED);
                schedule.setSyncNote("Cancelled due to assignment update");
                schedule.setLastSyncedAt(LocalDateTime.now());
            }
        }
        
        workScheduleRepository.saveAll(futureSchedules);
        log.info("Cancelled {} future work schedules for assignment {}", futureSchedules.size(), assignmentId);
        
        // Recreate work schedules with new schedule
        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
        
        if (requiresVerification(assignment)) {
            boolean isNewEmployee = isEmployeeNew(assignment.getEmployee().getId());
            WorkScheduleReason reason = isNewEmployee ? 
                WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION : 
                WorkScheduleReason.CONTRACT_REQUIREMENT;
            
            Long verificationId = null;
            if (isNewEmployee) {
                verificationId = verificationRepository
                    .findByAssignmentId(assignmentId)
                    .map(AssignmentVerification::getId)
                    .orElse(null);
            }
            
            LocalDate startDate = newStartDate.isAfter(today) ? newStartDate : today.plusDays(1);
            createWorkSchedulesForAssignment(assignment, reason, verificationId, startDate, newEndDate);
            log.info("Recreated work schedules for assignment {} from {} to {}", 
                assignmentId, startDate, newEndDate);
        }
    }
    
    @Override
    @Transactional
    public void handleAssignmentTermination(Long assignmentId, LocalDate terminationDate) {
        log.info("Handling assignment termination: assignmentId={}, terminationDate={}", 
            assignmentId, terminationDate);
        
        // Cancel all work schedules after termination date
        List<WorkSchedule> futureSchedules = workScheduleRepository
            .findByAssignmentIdAndScheduledDateAfter(assignmentId, terminationDate);
        
        for (WorkSchedule schedule : futureSchedules) {
            schedule.setStatus(WorkScheduleStatus.CANCELLED);
            schedule.setSyncNote("Cancelled due to assignment termination on " + terminationDate);
            schedule.setLastSyncedAt(LocalDateTime.now());
        }
        
        workScheduleRepository.saveAll(futureSchedules);
        log.info("Cancelled {} work schedules after termination date for assignment {}", 
            futureSchedules.size(), assignmentId);
    }
    
    @Override
    @Transactional
    public void handleReassignment(Long oldAssignmentId, Long newAssignmentId) {
        log.info("Handling reassignment: oldAssignmentId={}, newAssignmentId={}", 
            oldAssignmentId, newAssignmentId);
        
        // Cancel old assignment's future work schedules
        LocalDate today = LocalDate.now();
        List<WorkSchedule> oldSchedules = workScheduleRepository
            .findByAssignmentIdAndScheduledDateAfter(oldAssignmentId, today);
        
        for (WorkSchedule schedule : oldSchedules) {
            if (schedule.getStatus() == WorkScheduleStatus.SCHEDULED) {
                schedule.setStatus(WorkScheduleStatus.CANCELLED);
                schedule.setSyncNote("Cancelled due to reassignment to assignment " + newAssignmentId);
                schedule.setLastSyncedAt(LocalDateTime.now());
            }
        }
        
        workScheduleRepository.saveAll(oldSchedules);
        log.info("Cancelled {} work schedules for old assignment {}", oldSchedules.size(), oldAssignmentId);
        
        // Create work schedules for new assignment if needed
        Assignment newAssignment = assignmentRepository.findById(newAssignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("New assignment not found: " + newAssignmentId));
        
        if (requiresVerification(newAssignment)) {
            boolean isNewEmployee = isEmployeeNew(newAssignment.getEmployee().getId());
            WorkScheduleReason reason = isNewEmployee ? 
                WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION : 
                WorkScheduleReason.CONTRACT_REQUIREMENT;
            
            Long verificationId = null;
            if (isNewEmployee) {
                verificationId = verificationRepository
                    .findByAssignmentId(newAssignmentId)
                    .map(AssignmentVerification::getId)
                    .orElse(null);
            }
            
            LocalDate startDate = newAssignment.getStartDate().isAfter(today) ? 
                newAssignment.getStartDate() : today.plusDays(1);
            LocalDate endDate = newAssignment.getEndDate() != null ? 
                newAssignment.getEndDate() : LocalDate.now().plusMonths(1).withDayOfMonth(1).minusDays(1);
            
            createWorkSchedulesForAssignment(newAssignment, reason, verificationId, startDate, endDate);
            log.info("Created work schedules for new assignment {} from {} to {}", 
                newAssignmentId, startDate, endDate);
        }
    }

    // Helper methods

    private void checkAndAutoApprove(Long verificationId) {
        log.info("Checking auto-approval for verification: {}", verificationId);

        AssignmentVerification verification = verificationRepository.findById(verificationId)
            .orElse(null);
        if (verification == null) return;

        // Increment currentAttempts
        verification.incrementAttempts();
        if (verification.getStatus() == com.company.company_clean_hub_be.entity.VerificationStatus.PENDING
            && !verification.isCompleted()) {
            verification.setStatus(com.company.company_clean_hub_be.entity.VerificationStatus.IN_PROGRESS);
        }
        verificationRepository.save(verification);

        log.info("Verification {} now has {}/{} attempts", 
            verificationId, verification.getCurrentAttempts(), verification.getMaxAttempts());

        // Count VERIFIED schedules
        Long verifiedCount = workScheduleRepository.countByVerificationIdAndStatus(
            verificationId, WorkScheduleStatus.VERIFIED
        );

        log.info("Verification {} has {} verified schedules", verificationId, verifiedCount);

        if (verifiedCount >= 5) {
            log.info("Auto-approving verification {} (reached 5 verified schedules)", verificationId);

            if (!verification.isCompleted()) {
                verification.setStatus(com.company.company_clean_hub_be.entity.VerificationStatus.AUTO_APPROVED);
                verification.setAutoApprovedAt(LocalDateTime.now());
                verificationRepository.save(verification);

                // Create attendances for all remaining SCHEDULED work_schedules
                List<WorkSchedule> remainingSchedules = workScheduleRepository
                    .findByVerificationIdAndStatusIn(verificationId,
                        List.of(WorkScheduleStatus.SCHEDULED));

                for (WorkSchedule ws : remainingSchedules) {
                    try {
                        Attendance att = createAttendanceFromSchedule(ws);
                        ws.setAttendance(att);
                        ws.setStatus(WorkScheduleStatus.VERIFIED);
                        ws.setLastSyncedAt(LocalDateTime.now());
                        ws.setSyncNote("Auto-approved: attendance created");
                        workScheduleRepository.save(ws);
                    } catch (Exception e) {
                        log.error("Failed to create attendance for schedule {}: {}", ws.getId(), e.getMessage());
                    }
                }

                log.info("Auto-approved verification {} and created {} attendances", 
                    verificationId, remainingSchedules.size());

                // Update workDays and plannedDays on the assignment
                updateAssignmentDaysAfterApproval(verification.getAssignment().getId());
            }
        }
    }

    private void updateAssignmentDaysAfterApproval(Long assignmentId) {        try {
            Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment == null) return;

            // workDays = total VERIFIED work_schedules (each has an attendance)
            List<WorkSchedule> allVerified = workScheduleRepository.findByAssignmentId(assignmentId)
                .stream()
                .filter(ws -> ws.getStatus() == WorkScheduleStatus.VERIFIED)
                .collect(java.util.stream.Collectors.toList());
            int workDays = allVerified.size();

            // plannedDays = total work_schedules for this assignment (VERIFIED + SCHEDULED + MISSED)
            // i.e. all non-CANCELLED schedules
            int plannedDays = (int) workScheduleRepository.findByAssignmentId(assignmentId)
                .stream()
                .filter(ws -> ws.getStatus() != WorkScheduleStatus.CANCELLED)
                .count();

            assignment.setWorkDays(workDays);
            assignment.setPlannedDays(plannedDays);
            assignmentRepository.save(assignment);

            log.info("Updated assignment {}: workDays={}, plannedDays={}", assignmentId, workDays, plannedDays);
        } catch (Exception e) {
            log.error("Failed to update assignment days for assignmentId={}: {}", assignmentId, e.getMessage());
        }
    }

    private VerificationImage saveVerificationImage(WorkScheduleCaptureRequest request, WorkSchedule schedule) {
        try {
            // Upload to Cloudinary using storeBase64
            String fileName = "verification_" + schedule.getId() + "_" + System.currentTimeMillis();
            String publicId = fileStorageService.storeBase64(
                request.getImageBase64(),
                fileName,
                "company-clean-hub/verification"
            );
            String imageUrl = fileStorageService.getSecureUrl(publicId);

            VerificationImage image = VerificationImage.builder()
                .assignmentVerification(schedule.getAssignmentVerification()) // For verification purposes
                .employee(schedule.getEmployee())
                .attendance(null) // Will be linked to attendance after attendance is created (for attendance photo tracking)
                .cloudinaryPublicId(publicId)
                .cloudinaryUrl(imageUrl)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .address(request.getAddress())
                .faceConfidence(request.getFaceConfidence())
                .imageQualityScore(request.getImageQualityScore())
                .capturedAt(LocalDateTime.now())
                .build();

            return imageRepository.save(image);
        } catch (Exception e) {
            log.error("Failed to save verification image", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED, "Failed to save image");
        }
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

    private WorkScheduleResponse mapToResponse(WorkSchedule schedule) {
        return WorkScheduleResponse.builder()
            .id(schedule.getId())
            .assignmentId(schedule.getAssignment().getId())
            .employeeId(schedule.getEmployee().getId())
            .employeeName(schedule.getEmployee().getName())
            .contractId(schedule.getAssignment().getContract() != null ?
                schedule.getAssignment().getContract().getId() : null)
            .scheduledDate(schedule.getScheduledDate())
            .status(schedule.getStatus())
            .statusDescription(schedule.getStatus().getDescription())
            .reason(schedule.getReason())
            .reasonDescription(schedule.getReason().getDescription())
            .assignmentVerificationId(schedule.getAssignmentVerification() != null ? 
                schedule.getAssignmentVerification().getId() : null)
            .verificationImageId(schedule.getVerificationImage() != null ? 
                schedule.getVerificationImage().getId() : null)
            .attendanceId(schedule.getAttendance() != null ? 
                schedule.getAttendance().getId() : null)
            .photoCapturedAt(schedule.getPhotoCapturedAt())
            .canCapturePhoto(schedule.canCapturePhoto())
            .attendanceDeleted(schedule.getAttendanceDeleted())
            .syncNote(schedule.getSyncNote())
            .lastSyncedAt(schedule.getLastSyncedAt())
            .createdAt(schedule.getCreatedAt())
            .updatedAt(schedule.getUpdatedAt())
            .build();
    }

    @Override
    public com.company.company_clean_hub_be.dto.response.VerificationImageResponse getImageByWorkScheduleId(Long workScheduleId) {
        WorkSchedule schedule = workScheduleRepository.findById(workScheduleId)
            .orElseThrow(() -> new ResourceNotFoundException("Work schedule not found: " + workScheduleId));

        if (schedule.getVerificationImage() == null) {
            return null;
        }

        com.company.company_clean_hub_be.entity.VerificationImage img = schedule.getVerificationImage();
        return com.company.company_clean_hub_be.dto.response.VerificationImageResponse.builder()
            .id(img.getId())
            .verificationId(img.getAssignmentVerification() != null ? img.getAssignmentVerification().getId() : null)
            .employeeId(img.getEmployee().getId())
            .attendanceId(img.getAttendance() != null ? img.getAttendance().getId() : null)
            .cloudinaryPublicId(img.getCloudinaryPublicId())
            .cloudinaryUrl(img.getCloudinaryUrl())
            .latitude(img.getLatitude())
            .longitude(img.getLongitude())
            .address(img.getAddress())
            .capturedAt(img.getCapturedAt())
            .faceConfidence(img.getFaceConfidence())
            .imageQualityScore(img.getImageQualityScore())
            .createdAt(img.getCreatedAt())
            .build();
    }

    @Override
    public List<com.company.company_clean_hub_be.dto.response.WorkScheduleContractSummary> getContractsSummary(
            Integer month, Integer year, String sort) {

        LocalDate startDate;
        LocalDate endDate;
        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }

        List<WorkSchedule> allSchedules = workScheduleRepository.findAll().stream()
            .filter(ws -> !ws.getScheduledDate().isBefore(startDate) && !ws.getScheduledDate().isAfter(endDate))
            .collect(Collectors.toList());

        // Group by contract
        java.util.Map<Long, List<WorkSchedule>> byContract = allSchedules.stream()
            .filter(ws -> ws.getAssignment() != null && ws.getAssignment().getContract() != null)
            .collect(Collectors.groupingBy(ws -> ws.getAssignment().getContract().getId()));

        List<com.company.company_clean_hub_be.dto.response.WorkScheduleContractSummary> result = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, List<WorkSchedule>> entry : byContract.entrySet()) {
                Long contractId = entry.getKey();
                List<WorkSchedule> schedules = entry.getValue();
                com.company.company_clean_hub_be.entity.Contract contract =
                    schedules.get(0).getAssignment().getContract();

                long total = schedules.size();
                long verified = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.VERIFIED).count();
                long missed = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.MISSED).count();
                long scheduled = schedules.stream().filter(ws -> ws.getStatus() == WorkScheduleStatus.SCHEDULED).count();
                long employeeCount = schedules.stream()
                    .map(ws -> ws.getEmployee().getId()).distinct().count();

                result.add(com.company.company_clean_hub_be.dto.response.WorkScheduleContractSummary.builder()
                    .contractId(contractId)
                    .contractCode("HĐ-" + contractId)
                    .customerName(contract.getCustomer() != null ? contract.getCustomer().getName() : "")
                    .customerId(contract.getCustomer() != null ? contract.getCustomer().getId() : null)
                    .totalEmployees((int) employeeCount)
                    .totalSchedules(total)
                    .verifiedCount(verified)
                    .missedCount(missed)
                    .scheduledCount(scheduled)
                    .verifiedPercentage(total > 0 ? Math.round(verified * 100.0 / total * 100.0) / 100.0 : 0.0)
                    .build());
        }

        // Sort
        if ("missed".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> Long.compare(b.getMissedCount(), a.getMissedCount()));
        } else if ("verified".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> Double.compare(b.getVerifiedPercentage(), a.getVerifiedPercentage()));
        } else {
            result.sort((a, b) -> a.getCustomerName().compareTo(b.getCustomerName()));
        }

        return result;
    }
}
