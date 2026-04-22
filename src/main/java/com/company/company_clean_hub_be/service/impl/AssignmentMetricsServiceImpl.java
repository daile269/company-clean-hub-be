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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentMetricsServiceImpl implements AssignmentMetricsService {

    private final AssignmentRepository assignmentRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final AttendanceRepository attendanceRepository;

    @Override
    @Transactional
    public void updateAssignmentMetrics(Long assignmentId) {
        try {
            Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment == null) {
                log.warn("Assignment not found: {}", assignmentId);
                return;
            }

            // workDays = VERIFIED work_schedules + standalone attendances (no WorkSchedule)
            List<WorkSchedule> allSchedules = workScheduleRepository.findByAssignmentId(assignmentId);
            int verifiedSchedules = (int) allSchedules.stream()
                .filter(ws -> ws.getStatus() == WorkScheduleStatus.VERIFIED)
                .count();

            // Count standalone attendances (attendance records without a corresponding WorkSchedule)
            List<Attendance> allAttendances = attendanceRepository.findByAssignmentId(assignmentId);
            Set<Long> wsAttendanceIds = allSchedules.stream()
                .filter(ws -> ws.getAttendance() != null)
                .map(ws -> ws.getAttendance().getId())
                .collect(Collectors.toSet());
            int standaloneAttendances = (int) allAttendances.stream()
                .filter(a -> a.getDeleted() == null || !a.getDeleted())
                .filter(a -> !wsAttendanceIds.contains(a.getId()))
                .count();

            int workDays = verifiedSchedules + standaloneAttendances;

            // plannedDays = non-CANCELLED work_schedules + standalone attendances
            int plannedSchedules = (int) allSchedules.stream()
                .filter(ws -> ws.getStatus() != WorkScheduleStatus.CANCELLED)
                .count();
            int plannedDays = plannedSchedules + standaloneAttendances;

            assignment.setWorkDays(workDays);
            assignment.setPlannedDays(plannedDays);
            assignmentRepository.save(assignment);

            log.info("Updated assignment metrics: assignmentId={}, workDays={}, plannedDays={}", 
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
