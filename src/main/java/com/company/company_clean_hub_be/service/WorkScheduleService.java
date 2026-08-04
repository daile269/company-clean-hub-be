package com.company.company_clean_hub_be.service;

import java.time.LocalDate;
import java.util.List;

import com.company.company_clean_hub_be.dto.request.WorkScheduleCaptureRequest;
import com.company.company_clean_hub_be.dto.response.WorkScheduleResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleReason;

public interface WorkScheduleService {

    // Create work schedules
    List<WorkSchedule> createWorkSchedulesForAssignment(
        Assignment assignment, 
        WorkScheduleReason reason, 
        Long verificationId,
        LocalDate fromDate, 
        LocalDate toDate
    );

    // Create work schedules for specific dates (dùng cho SUPPORT assignment)
    List<WorkSchedule> createWorkSchedulesForDates(
        Assignment assignment,
        WorkScheduleReason reason,
        Long verificationId,
        List<LocalDate> dates
    );

    // Get work schedules
    List<WorkScheduleResponse> getWorkSchedulesByAssignment(Long assignmentId);
    
    List<WorkScheduleResponse> getWorkSchedulesByEmployee(Long employeeId, LocalDate startDate, LocalDate endDate);
    
    List<WorkScheduleResponse> getMissedSchedules(LocalDate startDate, LocalDate endDate);
    
    List<WorkScheduleResponse> getMissedSchedulesByEmployee(Long employeeId, LocalDate startDate, LocalDate endDate);
    
    WorkScheduleResponse getWorkScheduleById(Long id);
    
    // New APIs for frontend
    List<WorkScheduleResponse> getWorkSchedulesByDateRange(LocalDate startDate, LocalDate endDate, Long employeeId, String status);
    
    List<WorkScheduleResponse> getWorkSchedulesByDate(LocalDate date, String status);
    
    com.company.company_clean_hub_be.dto.response.WorkScheduleStatsResponse getStats(Integer month, Integer year, Long employeeId);
    
    List<com.company.company_clean_hub_be.dto.response.EmployeeScheduleSummary> getEmployeesWithSchedules(Integer month, Integer year);

    // Photo capture
    WorkScheduleResponse capturePhoto(WorkScheduleCaptureRequest request);

    // Mark missed (cron job)
    void markMissedCheckIns(LocalDate date);

    // Sync with attendance
    void syncAttendanceDeletion(Long attendanceId, Long userId);
    
    void syncAttendanceCreation(Long attendanceId);

    // Generate for future months
    void generateMonthlyWorkSchedules(LocalDate month);

    // Admin actions
    WorkScheduleResponse cancelWorkSchedule(Long id, String reason);
    
    WorkScheduleResponse createAttendanceForMissed(Long id, String reason);
    
    // Assignment lifecycle handlers
    void handleAssignmentUpdate(Long assignmentId, LocalDate newStartDate, LocalDate newEndDate);
    
    void handleAssignmentTermination(Long assignmentId, LocalDate terminationDate);
    
    void handleReassignment(Long oldAssignmentId, Long newAssignmentId);

    // Helper
    boolean canCapturePhoto(Long workScheduleId);

    // Check if employee has any pending SCHEDULED work schedules from today
    boolean hasPendingSchedules(Long employeeId);

    // Image
    com.company.company_clean_hub_be.dto.response.VerificationImageResponse getImageByWorkScheduleId(Long workScheduleId);

    // Contracts summary for management page
    List<com.company.company_clean_hub_be.dto.response.WorkScheduleContractSummary> getContractsSummary(Integer month, Integer year, String sort);
}
