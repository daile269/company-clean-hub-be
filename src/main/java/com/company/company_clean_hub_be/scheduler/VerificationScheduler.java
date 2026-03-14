package com.company.company_clean_hub_be.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.company_clean_hub_be.service.VerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job để tự động duyệt các verification đã đủ 5 lần chụp ảnh
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationScheduler {

    private final VerificationService verificationService;

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
}
