package com.company.company_clean_hub_be.schedule;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler để tự động kích hoạt các assignment SCHEDULED khi đến ngày startDate
 * Chạy mỗi ngày lúc 00:01 sáng
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentActivationScheduler {

    private final AssignmentRepository assignmentRepository;

    /**
     * Chạy mỗi ngày lúc 00:01 để activate các assignment SCHEDULED đến hạn
     * CHỈ chuyển status từ SCHEDULED sang IN_PROGRESS
     * Attendance đã được tạo sẵn khi tạo assignment, không cần tạo lại
     */
    @Scheduled(cron = "0 5 0 * * *")  // Chạy lúc 00:05 hàng ngày
    @Transactional
    public void activateScheduledAssignments() {
        LocalDate today = LocalDate.now();
        
        log.info("[ASSIGNMENT_ACTIVATION] Starting scheduled assignment activation check for {}", today);
        
        try {
            // Tìm tất cả assignment có status SCHEDULED và startDate = hôm nay
            List<Assignment> scheduledAssignments = assignmentRepository
                .findByStatusAndStartDate(AssignmentStatus.SCHEDULED, today);
            
            if (scheduledAssignments.isEmpty()) {
                log.info("[ASSIGNMENT_ACTIVATION] No scheduled assignments to activate for {}", today);
                return;
            }
            
            log.info("[ASSIGNMENT_ACTIVATION] Found {} scheduled assignments to activate", scheduledAssignments.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (Assignment assignment : scheduledAssignments) {
                try {
                    // Chuyển status sang IN_PROGRESS
                    assignment.setStatus(AssignmentStatus.IN_PROGRESS);
                    assignmentRepository.save(assignment);
                    
                    successCount++;
                    
                    log.info("[ASSIGNMENT_ACTIVATION] ✓ Activated assignment {}: employee={} ({}), contract={}, scope={}, startDate={}", 
                        assignment.getId(),
                        assignment.getEmployee().getId(),
                        assignment.getEmployee().getName(),
                        assignment.getContract() != null ? assignment.getContract().getId() : "N/A",
                        assignment.getScope(),
                        assignment.getStartDate());
                        
                    // Note: Attendance đã được tạo sẵn khi tạo assignment
                    // Cron này chỉ update status, không tạo attendance
                    
                } catch (Exception e) {
                    failCount++;
                    log.error("[ASSIGNMENT_ACTIVATION] ✗ Failed to activate assignment {}: {}", 
                        assignment.getId(), e.getMessage(), e);
                }
            }
            
            log.info("[ASSIGNMENT_ACTIVATION] Completed: {} success, {} failed out of {} total assignments", 
                successCount, failCount, scheduledAssignments.size());
                
        } catch (Exception e) {
            log.error("[ASSIGNMENT_ACTIVATION] Error during activation process: {}", e.getMessage(), e);
        }
    }
}
