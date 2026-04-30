package com.company.company_clean_hub_be.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.WorkScheduleRepository;
import com.company.company_clean_hub_be.service.AssignmentMetricsService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentMetricsServiceImpl implements AssignmentMetricsService {

    private final AssignmentRepository assignmentRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final AttendanceRepository attendanceRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void updateAssignmentMetrics(Long assignmentId) {
        try {
            Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment == null) {
                log.warn("Assignment not found: {}", assignmentId);
                return;
            }

            // Flush pending changes so queries below see the latest state
            entityManager.flush();

            // workDays = VERIFIED work_schedules (with attendance not deleted) + standalone attendances (no WorkSchedule)
            List<WorkSchedule> allSchedules = workScheduleRepository.findByAssignmentId(assignmentId);
            
            // Only count VERIFIED WorkSchedules that still have valid attendance (not deleted)
            int verifiedSchedules = (int) allSchedules.stream()
                .filter(ws -> ws.getStatus() == WorkScheduleStatus.VERIFIED)
                .filter(ws -> ws.getAttendanceDeleted() == null || !ws.getAttendanceDeleted())
                .count();

            // Count standalone attendances (attendance records without a corresponding WorkSchedule)
            List<Attendance> allAttendances = attendanceRepository.findByAssignmentId(assignmentId);
            
            // Build set of attendance IDs that have WorkSchedule (including deleted ones)
            // We need to check both current link and attendanceDeleted flag
            Set<Long> wsAttendanceIds = allSchedules.stream()
                .filter(ws -> ws.getAttendance() != null)
                .map(ws -> ws.getAttendance().getId())
                .collect(Collectors.toSet());
            
            // Also find attendances that were unlinked (attendanceDeleted = true)
            // by matching date between WorkSchedule and Attendance
            Set<java.time.LocalDate> wsScheduledDates = allSchedules.stream()
                .filter(ws -> ws.getAttendanceDeleted() != null && ws.getAttendanceDeleted())
                .map(ws -> ws.getScheduledDate())
                .collect(Collectors.toSet());
            
            // For workDays: only count non-deleted standalone attendances
            int standaloneAttendancesForWork = (int) allAttendances.stream()
                .filter(a -> a.getDeleted() == null || !a.getDeleted())
                .filter(a -> !wsAttendanceIds.contains(a.getId()))
                .filter(a -> !wsScheduledDates.contains(a.getDate())) // Exclude if has WorkSchedule by date
                .count();

            int workDays = verifiedSchedules + standaloneAttendancesForWork;

            // plannedDays: giữ nguyên giá trị cũ, không tính lại.
            // plannedDays phản ánh số ngày dự kiến làm theo lịch hợp đồng,
            // không bị ảnh hưởng bởi việc nhân viên nghỉ phép hay xóa attendance.
            int plannedDays = assignment.getPlannedDays() != null ? assignment.getPlannedDays() : 0;

            assignmentRepository.updateMetrics(assignmentId, workDays, plannedDays);

            log.info("Updated assignment metrics: assignmentId={}, workDays={}, plannedDays={} (preserved)", 
                assignmentId, workDays, plannedDays);
        } catch (Exception e) {
            log.error("Failed to update assignment metrics for assignmentId={}: {}", 
                assignmentId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void updateMultipleAssignmentMetrics(List<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return;
        }

        log.info("Updating metrics for {} assignments", assignmentIds.size());
        for (Long assignmentId : assignmentIds) {
            updateAssignmentMetrics(assignmentId);
        }
    }
}
