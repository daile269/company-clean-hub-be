package com.company.company_clean_hub_be.schedule;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.AssignmentType;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentScheduler {

    private final AssignmentRepository assignmentRepository;

    /**
     * Chạy lúc 1h sáng hàng ngày để cập nhật trạng thái các phân công tạm thời đã qua
     * Cron: 0 0 1 * * * = giây phút giờ ngày tháng thứ
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void updateExpiredTemporaryAssignments() {
        executeUpdateExpiredTemporaryAssignments();
    }

    /**
     * Method public để có thể gọi test từ controller
     */
    @Transactional
    public void executeUpdateExpiredTemporaryAssignments() {
        log.info("=== BẮT ĐẦU QUÉT CẬP NHẬT PHÂN CÔNG TẠM THỜI ===");
        log.info("Thời gian chạy: {}", LocalDateTime.now());

        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        try {
            // Tìm tất cả các phân công TEMPORARY có startDate < hôm nay và status IN_PROGRESS
            List<Assignment> expiredAssignments = assignmentRepository.findExpiredTemporaryAssignments(yesterday);
            
            log.info("Tìm thấy {} phân công tạm thời đã hết hạn", expiredAssignments.size());

            if (!expiredAssignments.isEmpty()) {
                int updatedCount = 0;
                
                for (Assignment assignment : expiredAssignments) {
                    log.info("Cập nhật assignment ID: {} - Employee: {} - StartDate: {} - Status: {} → COMPLETED",
                            assignment.getId(),
                            assignment.getEmployee().getName(),
                            assignment.getStartDate(),
                            assignment.getStatus());
                    
                    assignment.setStatus(AssignmentStatus.COMPLETED);
                    assignment.setUpdatedAt(LocalDateTime.now());
                    assignmentRepository.save(assignment);
                    updatedCount++;
                }
                
                log.info("✓ Đã cập nhật {} phân công tạm thời sang trạng thái COMPLETED", updatedCount);
            } else {
                log.info("Không có phân công tạm thời nào cần cập nhật");
            }
            
        } catch (Exception e) {
            log.error("❌ LỖI khi cập nhật phân công tạm thời: {}", e.getMessage(), e);
        }
        
        log.info("=== KẾT THÚC QUÉT CẬP NHẬT PHÂN CÔNG TẠM THỜI ===\n");
    }
}
