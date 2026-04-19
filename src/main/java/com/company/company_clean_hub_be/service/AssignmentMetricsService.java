package com.company.company_clean_hub_be.service;

/**
 * Service để tính toán và cập nhật các metrics của Assignment
 * Bao gồm: workDays, plannedDays
 */
public interface AssignmentMetricsService {
    
    /**
     * Cập nhật workDays và plannedDays cho Assignment
     * - workDays: Tổng số WorkSchedule có status = VERIFIED (đã có attendance)
     * - plannedDays: Tổng số WorkSchedule không bị CANCELLED
     * 
     * @param assignmentId ID của assignment cần cập nhật
     */
    void updateAssignmentMetrics(Long assignmentId);
    
    /**
     * Cập nhật metrics cho nhiều assignments cùng lúc
     * 
     * @param assignmentIds Danh sách ID của các assignment cần cập nhật
     */
    void updateMultipleAssignmentMetrics(java.util.List<Long> assignmentIds);
}
