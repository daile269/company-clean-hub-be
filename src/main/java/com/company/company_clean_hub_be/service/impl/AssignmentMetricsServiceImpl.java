package com.company.company_clean_hub_be.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.WorkSchedule;
import com.company.company_clean_hub_be.entity.WorkScheduleStatus;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
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

    @Override
    @Transactional
    public void updateAssignmentMetrics(Long assignmentId) {
        try {
            Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment == null) {
                log.warn("Assignment not found: {}", assignmentId);
                return;
            }

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
