package com.company.company_clean_hub_be.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.repository.*;
import com.company.company_clean_hub_be.service.NotificationService;
import com.company.company_clean_hub_be.service.SalaryNoteValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled jobs cho hệ thống thông báo mở rộng.
 * Mỗi job kiểm tra một loại điều kiện và tạo notification nếu phát hiện vấn đề.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final ContractRepository contractRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentVerificationRepository assignmentVerificationRepository;
    private final VerificationImageRepository verificationImageRepository;
    private final NotificationService notificationService;
    private final SalaryNoteValidator salaryNoteValidator;

    /**
     * Kiểm tra nhân viên quên chụp hình xác minh (chạy mỗi 30 phút từ 6h-20h).
     * Logic: sau 2 tiếng kể từ workStartTime của hợp đồng mà NV chưa chụp ảnh → thông báo.
     */
    @Scheduled(cron = "0 0,30 6-20 * * *")
    @Transactional
    public void checkMissingVerificationPhotos() {
        log.info("[NOTIF-SCHEDULER] Checking missing verification photos...");
        try {
            List<Assignment> activeAssignments = assignmentRepository
                    .findByStatusAndContractRequiresImageVerification(
                            AssignmentStatus.IN_PROGRESS, true);
            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            for (Assignment assignment : activeAssignments) {
                Contract contract = assignment.getContract();
                // Chỉ kiểm tra nếu contract có workStartTime và đã quá 2 tiếng
                if (contract == null || contract.getWorkStartTime() == null) {
                    continue;
                }
                LocalDateTime deadline = today.atTime(contract.getWorkStartTime()).plusHours(2);
                if (now.isBefore(deadline)) {
                    continue; // Chưa đến hạn 2 tiếng sau giờ bắt đầu
                }

                java.util.Optional<AssignmentVerification> verificationOpt = assignmentVerificationRepository
                        .findByAssignmentId(assignment.getId());
                if (verificationOpt.isPresent()) {
                    AssignmentVerification verification = verificationOpt.get();
                    if (verification.getStatus() != VerificationStatus.APPROVED
                            && verification.getStatus() != VerificationStatus.AUTO_APPROVED
                            && verification.getStatus() != VerificationStatus.BYPASS_APPROVED) {
                        boolean hasPhotoToday = verificationImageRepository
                                .existsByVerificationIdAndCapturedDate(verification.getId(), now);
                        if (!hasPhotoToday) {
                            createNotification(
                                    NotificationType.MISSING_VERIFICATION_PHOTO,
                                    "Nhân viên quên chụp hình xác minh",
                                    String.format("Nhân viên %s (MS: %s) chưa chụp hình xác minh hôm nay (%s) — đã quá 2 tiếng sau giờ làm việc (%s)",
                                            assignment.getEmployee().getName(),
                                            assignment.getEmployee().getEmployeeCode(),
                                            today,
                                            contract.getWorkStartTime()),
                                    assignment.getEmployee().getId(),
                                    assignment.getId(),
                                    contract);
                        }
                    }
                }
            }
            log.info("[NOTIF-SCHEDULER] Missing verification photo check completed.");
        } catch (Exception e) {
            log.error("[NOTIF-SCHEDULER] Error checking missing verification photos: {}", e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra hợp đồng sắp hết hạn (mỗi ngày lúc 8:00 sáng).
     * Chỉ áp dụng cho MONTHLY_FIXED và MONTHLY_ACTUAL.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkContractExpiring() {
        log.info("[NOTIF-SCHEDULER] Checking expiring contracts...");
        try {
            LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
            List<Contract> expiringContracts = contractRepository
                    .findByEndDateBetweenAndPaymentStatusNot(LocalDate.now(), thirtyDaysFromNow, "TERMINATED");
            for (Contract contract : expiringContracts) {
                // Chỉ áp dụng cho hợp đồng MONTHLY_FIXED và MONTHLY_ACTUAL
                if (contract.getContractType() != ContractType.MONTHLY_FIXED
                        && contract.getContractType() != ContractType.MONTHLY_ACTUAL) {
                    continue;
                }
                createNotification(
                        NotificationType.CONTRACT_EXPIRING,
                        "Hợp đồng sắp hết hạn",
                        String.format("Hợp đồng '%s' (%s) sẽ hết hạn vào ngày %s",
                                contract.getDescription(),
                                contract.getContractType() != null ? contract.getContractType().name() : "Không xác định",
                                contract.getEndDate()),
                        null, null, contract);
            }
            log.info("[NOTIF-SCHEDULER] Expiring contract check completed. Found: {}", expiringContracts.size());
        } catch (Exception e) {
            log.error("[NOTIF-SCHEDULER] Error checking expiring contracts: {}", e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra FIXED_BY_DAY làm việc quá 5 ngày liên tiếp (mỗi ngày lúc 7:00 sáng).
     *
     * R3: target là FIXED_BY_DAY (KHÔNG phải TEMPORARY), streak đếm ngày làm việc liên tiếp
     * riêng từng hợp đồng, bỏ qua cuối tuần/ngày nghỉ. Báo tại ngày 6, 11, 16... (streak % 5 == 1).
     * Recipients: QLT1 (tất cả) + QLT2 (theo customer được phân công), KHÔNG gửi QLV.
     */
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void checkTemporaryOver5Days() {
        log.info("[NOTIF-SCHEDULER] Checking FIXED_BY_DAY streak over 5 days...");
        try {
            List<Assignment> fixedByDayAssignments = assignmentRepository
                    .findByAssignmentTypeAndStatus(AssignmentType.FIXED_BY_DAY, AssignmentStatus.IN_PROGRESS);

            Set<String> checkedKeys = new HashSet<>();
            for (Assignment assignment : fixedByDayAssignments) {
                if (assignment.getEmployee() == null || assignment.getContract() == null) continue;
                Long employeeId = assignment.getEmployee().getId();
                Long contractId = assignment.getContract().getId();
                String key = employeeId + ":" + contractId;
                if (!checkedKeys.add(key)) continue;

                try {
                    // R3 precondition: hợp đồng phải có đủ 2 ghi chú lương DAILY (FIXED + TEMPORARY)
                    // và lương phân công nằm STRICTLY trong khoảng (FIXED, TEMPORARY).
                    if (!salaryNoteValidator.isEligibleForStreak(assignment.getContract(), assignment.getSalaryAtTime())) {
                        continue;
                    }

                    SalaryNoteValidator.StreakResult result = salaryNoteValidator
                            .calculateFixedByDayStreak(employeeId, contractId);
                    int streak = result.streakDays();
                    if (!salaryNoteValidator.shouldNotify(streak)) continue;

                    Contract contract = contractRepository.findById(contractId).orElse(null);
                    Employee employee = assignment.getEmployee();
                    String title = "Cảnh báo nhân viên làm tạm thời quá số ngày quy định";
                    String contractLabel = (contract != null && contract.getDescription() != null && !contract.getDescription().isBlank())
                            ? "Hợp đồng #" + contract.getId() + " - " + contract.getDescription()
                            : "Hợp đồng #" + contractId;
                    String message = String.format(
                            "Nhân viên %s (%s) đã làm việc %d ngày liên tiếp (phân công cố định theo ngày) tại %s. Vui lòng kiểm tra.",
                            employee.getName(), employee.getEmployeeCode(), streak, contractLabel);

                    // QLT1 (tất cả) + QLT2 (theo customer được phân công), dedup theo user
                    List<User> managers = notificationService.getRecipientsForContract(contract);

                    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                    for (User manager : managers) {
                        boolean exists = notificationRepository
                                .existsByTypeAndRefContractIdAndRecipientIdAndCreatedAtAfter(
                                        NotificationType.TEMPORARY_OVER_5_DAYS, contractId, manager.getId(), todayStart);
                        if (!exists) {
                            Notification notification = Notification.builder()
                                    .recipient(manager)
                                    .type(NotificationType.TEMPORARY_OVER_5_DAYS)
                                    .title(title)
                                    .message(message)
                                    .refContractId(contractId)
                                    .isRead(false)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            notificationRepository.save(notification);
                        }
                    }
                } catch (Exception e) {
                    log.error("[NOTIF-SCHEDULER] Error checking FIXED_BY_DAY streak for employee={}, contract={}: {}",
                            employeeId, contractId, e.getMessage());
                }
            }
            log.info("[NOTIF-SCHEDULER] FIXED_BY_DAY streak check completed.");
        } catch (Exception e) {
            log.error("[NOTIF-SCHEDULER] Error checking FIXED_BY_DAY streak: {}", e.getMessage(), e);
        }
    }

    private void createNotification(NotificationType type, String title, String message,
                                     Long refEmployeeId, Long refAssignmentId, Contract contract) {
        // Gửi cho tất cả QLT1 và QLT2
        List<User> managers = notificationService.getRecipientsForContract(contract);
        Long refContractId = contract != null ? contract.getId() : null;
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        for (User manager : managers) {
            // Dedup per recipient: mỗi manager chỉ nhận 1 notification/ngày cho cùng 1 issue
            boolean exists;
            if (refEmployeeId != null) {
                exists = notificationRepository.existsByTypeAndContractIdAndEmployeeIdAndRecipientIdAndCreatedAtAfter(
                        type, refContractId, refEmployeeId, manager.getId(), todayStart);
            } else {
                exists = notificationRepository.existsByTypeAndRefContractIdAndRecipientIdAndCreatedAtAfter(
                        type, refContractId, manager.getId(), todayStart);
            }
            if (!exists) {
                Notification notification = Notification.builder()
                        .recipient(manager)
                        .type(type)
                        .title(title)
                        .message(message)
                        .refEmployeeId(refEmployeeId)
                        .refAssignmentId(refAssignmentId)
                        .refContractId(refContractId)
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationRepository.save(notification);
            }
        }
    }
}
