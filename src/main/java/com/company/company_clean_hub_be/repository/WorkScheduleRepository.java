package com.company.company_clean_hub_be.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleReason;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    // Find by assignment
    List<WorkSchedule> findByAssignmentId(Long assignmentId);

    // Find by employee
    List<WorkSchedule> findByEmployeeId(Long employeeId);

    // Find by date
    List<WorkSchedule> findByScheduledDate(LocalDate date);

    // Find by assignment and date
    Optional<WorkSchedule> findByAssignmentIdAndScheduledDate(Long assignmentId, LocalDate date);

    // Find by status
    List<WorkSchedule> findByStatus(WorkScheduleStatus status);

    // Find by status and date
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.scheduledDate = :date AND ws.status = :status")
    List<WorkSchedule> findByScheduledDateAndStatus(@Param("date") LocalDate date, @Param("status") WorkScheduleStatus status);

    // Find by verification
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignmentVerification.id = :verificationId")
    List<WorkSchedule> findByVerificationId(@Param("verificationId") Long verificationId);

    // Find by verification and status
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignmentVerification.id = :verificationId AND ws.status IN :statuses")
    List<WorkSchedule> findByVerificationIdAndStatusIn(@Param("verificationId") Long verificationId, @Param("statuses") List<WorkScheduleStatus> statuses);

    // Count verified by verification
    @Query("SELECT COUNT(ws) FROM WorkSchedule ws WHERE ws.assignmentVerification.id = :verificationId AND ws.status = 'VERIFIED'")
    Long countByVerificationIdAndStatus(@Param("verificationId") Long verificationId, @Param("status") WorkScheduleStatus status);

    // Find by attendance
    Optional<WorkSchedule> findByAttendanceId(Long attendanceId);

    // Find by assignment and date range
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignment.id = :assignmentId AND ws.scheduledDate BETWEEN :startDate AND :endDate")
    List<WorkSchedule> findByAssignmentIdAndDateRange(@Param("assignmentId") Long assignmentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Find by employee and date range
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.employee.id = :employeeId AND ws.scheduledDate BETWEEN :startDate AND :endDate")
    List<WorkSchedule> findByEmployeeIdAndDateRange(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Find by reason
    List<WorkSchedule> findByReason(WorkScheduleReason reason);

    // Find by assignment and reason
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignment.id = :assignmentId AND ws.reason = :reason")
    List<WorkSchedule> findByAssignmentIdAndReason(@Param("assignmentId") Long assignmentId, @Param("reason") WorkScheduleReason reason);

    // Check if exists
    boolean existsByAssignmentIdAndScheduledDate(Long assignmentId, LocalDate date);

    // Find schedules needing attention (MISSED and not handled)
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.status = 'MISSED' AND ws.attendanceDeleted = false")
    List<WorkSchedule> findMissedSchedules();
    
    // Find MISSED schedules by date range
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.status = 'MISSED' AND ws.scheduledDate BETWEEN :startDate AND :endDate")
    List<WorkSchedule> findMissedSchedulesByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Find MISSED schedules by employee and date range
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.employee.id = :employeeId AND ws.status = 'MISSED' AND ws.scheduledDate BETWEEN :startDate AND :endDate")
    List<WorkSchedule> findMissedSchedulesByEmployeeAndDateRange(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Find future schedules by assignment
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignment.id = :assignmentId AND ws.scheduledDate > :date")
    List<WorkSchedule> findFutureSchedulesByAssignment(@Param("assignmentId") Long assignmentId, @Param("date") LocalDate date);
    
    // Find schedules after date by assignment
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignment.id = :assignmentId AND ws.scheduledDate > :date")
    List<WorkSchedule> findByAssignmentIdAndScheduledDateAfter(@Param("assignmentId") Long assignmentId, @Param("date") LocalDate date);

    // Find schedules from date onwards (inclusive) by assignment and status
    @Query("SELECT ws FROM WorkSchedule ws WHERE ws.assignment.id = :assignmentId AND ws.scheduledDate >= :date AND ws.status = :status")
    List<WorkSchedule> findByAssignmentIdAndScheduledDateFromAndStatus(
        @Param("assignmentId") Long assignmentId,
        @Param("date") LocalDate date,
        @Param("status") WorkScheduleStatus status);

    // Count verified schedules by employee and reason (across all assignments)
    @Query("SELECT COUNT(ws) FROM WorkSchedule ws WHERE ws.employee.id = :employeeId AND ws.status = 'VERIFIED' AND ws.reason = :reason")
    Long countVerifiedSchedulesByEmployeeAndReason(@Param("employeeId") Long employeeId, @Param("reason") WorkScheduleReason reason);

    // Delete by assignment (for cleanup)
    void deleteByAssignmentId(Long assignmentId);
}
