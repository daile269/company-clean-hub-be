package com.company.company_clean_hub_be.schedule;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.entity.VerificationImage;
import com.company.company_clean_hub_be.repository.VerificationImageRepository;
import com.company.company_clean_hub_be.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler tự động xoá ảnh xác minh trên Cloudinary sau 90 ngày.
 * Giữ lại record với GPS location + thời gian, chỉ xoá ảnh + clear URL.
 * Chạy mỗi ngày lúc 3:00 sáng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageCleanupScheduler {

    private final VerificationImageRepository verificationImageRepository;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = "0 0 3 * * *") // 3:00 AM daily
    @Transactional
    public void cleanupOldImages() {
        log.info("[IMAGE-CLEANUP] Starting scheduled image cleanup...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        List<VerificationImage> oldImages = verificationImageRepository
                .findImagesOlderThanWithPublicId(cutoffDate);

        if (oldImages.isEmpty()) {
            log.info("[IMAGE-CLEANUP] No images older than 90 days to clean up.");
            return;
        }

        log.info("[IMAGE-CLEANUP] Found {} images older than 90 days. Starting cleanup...", oldImages.size());

        int successCount = 0;
        int failCount = 0;

        for (VerificationImage image : oldImages) {
            try {
                if (image.getCloudinaryPublicId() != null && !image.getCloudinaryPublicId().isEmpty()) {
                    fileStorageService.deleteFile(image.getCloudinaryPublicId());
                    log.debug("[IMAGE-CLEANUP] Deleted Cloudinary image: publicId={}", image.getCloudinaryPublicId());
                }
                // Clear Cloudinary data, keep GPS + time
                image.setCloudinaryPublicId(null);
                image.setCloudinaryUrl(null);
                verificationImageRepository.save(image);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("[IMAGE-CLEANUP] Failed to cleanup image id={}: {}", image.getId(), e.getMessage());
            }
        }

        log.info("[IMAGE-CLEANUP] Cleanup completed: {} success, {} failed out of {} images",
                successCount, failCount, oldImages.size());
    }
}
