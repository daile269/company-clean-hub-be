package com.company.company_clean_hub_be.scheduler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.company_clean_hub_be.service.VerificationService;
import com.company.company_clean_hub_be.service.WorkScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job để tự động duyệt các verification đã đủ 5 lần chụp ảnh
 * và đánh dấu các work schedule bị missed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationScheduler {

    private final VerificationService verificationService;
    private final WorkScheduleService workScheduleService;

    /**
     * Chạy mỗi ngày lúc 1:00 AM để tự động duyệt verification
     * Cron format: giây phút giờ ngày tháng thứ
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void autoApproveVerifications() {
        log.info("=== Starting scheduled auto-approval job ===");
        try {
            verificationService.processAutoApprovals();
            log.info("=== Completed scheduled auto-approval job ===");
        } catch (Exception e) {
            log.error("=== Error in scheduled auto-approval job ===", e);
        }
    }
    
    /**
     * Chạy mỗi 6 giờ để đảm bảo không bỏ sót
     * Backup job chạy lúc 7:00, 13:00, 19:00
     */
    @Scheduled(cron = "0 0 7,13,19 * * *")
    public void autoApproveVerificationsBackup() {
        log.info("=== Starting backup auto-approval job ===");
        try {
            verificationService.processAutoApprovals();
            log.info("=== Completed backup auto-approval job ===");
        } catch (Exception e) {
            log.error("=== Error in backup auto-approval job ===", e);
        }
    }

    /**
     * Chạy mỗi ngày lúc 23:00 để đánh dấu các work schedule bị missed
     * Nhân viên có thể chụp ảnh từ 00:00 đến 22:59
     */
    @Scheduled(cron = "0 0 23 * * *")
    public void markMissedCheckIns() {
        log.info("=== Starting mark missed check-ins job ===");
        try {
            LocalDate today = LocalDate.now();
            workScheduleService.markMissedCheckIns(today);
            log.info("=== Completed mark missed check-ins job ===");
        } catch (Exception e) {
            log.error("=== Error in mark missed check-ins job ===", e);
        }
    }
    
    /**
     * Chạy vào 02:00 ngày 1 hàng tháng để tạo work schedules cho tháng mới
     * QUAN TRỌNG: Chạy SAU AssignmentScheduler.generateMonthlyAttendances() (01:00) 
     * để đảm bảo assignment mới đã được tạo trước
     * Chỉ tạo cho các assignment có bật verification
     */
    @Scheduled(cron = "0 0 2 1 * *")
    public void generateMonthlyWorkSchedules() {
        log.info("=== Starting monthly work schedule generation job ===");
        try {
            LocalDate nextMonth = LocalDate.now();
            workScheduleService.generateMonthlyWorkSchedules(nextMonth);
            log.info("=== Completed monthly work schedule generation job ===");
        } catch (Exception e) {
            log.error("=== Error in monthly work schedule generation job ===", e);
        }
    }
}
