package com.company.company_clean_hub_be.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.repository.AssignmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentScheduler {

    private final AssignmentRepository assignmentRepository;

    /**
     * Tự động quét và chuyển trạng thái sang COMPLETED cho các phân công đã hết hạn
     * (dựa trên assignment.endDate hoặc contract.endDate đã qua ngày hiện tại).
     * Chạy định kỳ 00:05 sáng hàng ngày và 1 lần khi ứng dụng khởi động.
     */
    @Scheduled(cron = "0 5 0 * * ?")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void autoCompleteExpiredAssignments() {
        LocalDate today = LocalDate.now();
        log.info("[SCHEDULER] Checking expired assignments as of {}", today);

        try {
            List<Assignment> expiredAssignments = assignmentRepository.findAll().stream()
                    .filter(a -> a.getStatus() == AssignmentStatus.IN_PROGRESS || a.getStatus() == AssignmentStatus.SCHEDULED)
                    .filter(a -> {
                        // Trường hợp 1: Phân công có endDate riêng và endDate < today
                        if (a.getEndDate() != null && a.getEndDate().isBefore(today)) {
                            return true;
                        }
                        // Trường hợp 2: Hợp đồng có endDate và contract.endDate < today
                        if (a.getContract() != null && a.getContract().getEndDate() != null 
                                && a.getContract().getEndDate().isBefore(today)) {
                            return true;
                        }
                        return false;
                    })
                    .toList();

            if (!expiredAssignments.isEmpty()) {
                for (Assignment a : expiredAssignments) {
                    a.setStatus(AssignmentStatus.COMPLETED);
                }
                assignmentRepository.saveAll(expiredAssignments);
                log.info("[SCHEDULER] Successfully updated {} expired assignments to COMPLETED status", expiredAssignments.size());
            } else {
                log.info("[SCHEDULER] No expired assignments to update");
            }
        } catch (Exception e) {
            log.error("[SCHEDULER] Error while running autoCompleteExpiredAssignments", e);
        }
    }
}
