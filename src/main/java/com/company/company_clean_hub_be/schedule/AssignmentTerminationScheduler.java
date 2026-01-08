package com.company.company_clean_hub_be.schedule;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.DeletedAttendanceBackup;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.DeletedAttendanceBackupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Scheduler để tự động chuyển các assignment có endDate = hôm nay sang TERMINATED
 * Chạy mỗi ngày lúc 00:01 sáng
 * 
 * Lưu ý: Attendance tương lai đã được xóa (backup) lúc call API terminate,
 * scheduler này chỉ chuyển status và cập nhật workDays
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentTerminationScheduler {

    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;

    /**
     * Chạy mỗi ngày lúc 00:01 để xử lý các assignment có endDate = hôm nay
     * Chuyển status từ IN_PROGRESS sang TERMINATED
     * (Attendance đã được xóa lúc call API, không cần xóa lại)
     */
    @Scheduled(cron = "0 1 0 * * *")  // Chạy lúc 00:01 hàng ngày
    @Transactional
    public void processScheduledTerminations() {
        LocalDate today = LocalDate.now();
        
        log.info("[TERMINATION_SCHEDULER] Starting scheduled termination processing for {}", today);
        
        try {
            // Tìm tất cả assignment có endDate = hôm nay và status = IN_PROGRESS
            List<Assignment> pendingTerminations = assignmentRepository
                .findAllByEndDateAndStatus(today, AssignmentStatus.IN_PROGRESS);
            
            if (pendingTerminations.isEmpty()) {
                log.info("[TERMINATION_SCHEDULER] No pending terminations for {}", today);
                return;
            }
            
            log.info("[TERMINATION_SCHEDULER] Found {} assignments to terminate", pendingTerminations.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (Assignment assignment : pendingTerminations) {
                try {
                    log.info("[TERMINATION_SCHEDULER] Processing assignment {}: employee={} ({}), contract={}", 
                        assignment.getId(),
                        assignment.getEmployee().getId(),
                        assignment.getEmployee().getName(),
                        assignment.getContract() != null ? assignment.getContract().getId() : "N/A");
                    
                    // Attendance tương lai đã được xóa lúc call API terminate
                    // Scheduler chỉ cần chuyển status và cập nhật workDays
                    
                    LocalDateTime now = LocalDateTime.now();
                    
                    // Chuyển status sang TERMINATED
                    assignment.setStatus(AssignmentStatus.TERMINATED);
                    assignment.setUpdatedAt(now);
                    
                    // Cập nhật workDays dựa trên attendance còn lại (đến hôm nay)
                    YearMonth ym = YearMonth.from(today);
                    LocalDate monthStart = ym.atDay(1);
                    int remainingWorkDays = attendanceRepository
                        .findByAssignmentAndDateBetween(assignment.getId(), monthStart, today)
                        .size();
                    assignment.setWorkDays(remainingWorkDays);
                    
                    assignmentRepository.save(assignment);
                    successCount++;
                    
                    log.info("[TERMINATION_SCHEDULER] ✓ Terminated assignment {}: workDays={} (attendances already deleted via API)", 
                        assignment.getId(), remainingWorkDays);
                        
                } catch (Exception e) {
                    failCount++;
                    log.error("[TERMINATION_SCHEDULER] ✗ Failed to terminate assignment {}: {}", 
                        assignment.getId(), e.getMessage(), e);
                }
            }
            
            log.info("[TERMINATION_SCHEDULER] Completed: {} success, {} failed out of {} total assignments", 
                successCount, failCount, pendingTerminations.size());
                
        } catch (Exception e) {
            log.error("[TERMINATION_SCHEDULER] Error during termination processing: {}", e.getMessage(), e);
        }
    }
}
